package com.safe_ride.rides_service.service;

import com.safe_ride.rides_service.config.UserContext;
import com.safe_ride.rides_service.exceptions.ConflictException;
import com.safe_ride.rides_service.exceptions.ForbiddenException;
import com.safe_ride.rides_service.exceptions.NotFoundException;
import com.safe_ride.rides_service.exceptions.RoleNotAllowedException;
import com.safe_ride.rides_service.model.dtos.AvailableRideResponse;
import com.safe_ride.rides_service.model.dtos.CreateRideRequest;
import com.safe_ride.rides_service.model.dtos.RideDetailsResponse;
import com.safe_ride.rides_service.model.dtos.RideResponse;
import com.safe_ride.rides_service.model.dtos.RideStatsResponse;
import com.safe_ride.rides_service.model.entity.Gender;
import com.safe_ride.rides_service.model.entity.Ride;
import com.safe_ride.rides_service.model.entity.RideParticipants;
import com.safe_ride.rides_service.model.entity.RideStatus;
import com.safe_ride.rides_service.model.mapper.RideMapper;
import com.safe_ride.rides_service.repo.RideParticipantsRepository;
import com.safe_ride.rides_service.repo.RideRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

@Service
public class RideService {
    /** Only rides whose pickup is within this many km of the rider are shown. */
    private static final double SEARCH_RADIUS_KM = 5.0;

    private final RideRepository rideRepository;
    private final RideParticipantsRepository rideParticipantsRepository;
    private final RideMapper rideMapper;
    private final PriceService priceService;

    public RideService(RideRepository rideRepository,
                       RideParticipantsRepository rideParticipantsRepository,
                       RideMapper rideMapper,
                       PriceService priceService) {
        this.rideRepository = rideRepository;
        this.rideParticipantsRepository = rideParticipantsRepository;
        this.rideMapper = rideMapper;
        this.priceService = priceService;
    }

    public RideResponse createRide(CreateRideRequest request) {
        UserContext ctx = getCurrentUserContext();
        if (!"PASSENGER".equals(ctx.role())) {
            throw new RoleNotAllowedException("Only passengers can request a ride.");
        }

        boolean hasActive = rideRepository.existsByCreatedByUserIdAndStatusIn(
                ctx.userId(), List.of(RideStatus.PENDING, RideStatus.ACCEPTED));
        if (hasActive) {
            throw new ConflictException(
                    "You already have an active ride. Cancel it before creating a new one.");
        }

        Ride ride = rideMapper.toRide(request);
        ride.setCreatedByUserId(ctx.userId());
        ride.setGender(Gender.valueOf(ctx.gender()));
        ride.setStatus(RideStatus.PENDING);

        return rideMapper.toResponse(rideRepository.save(ride));
    }

    @Transactional
    public void cancelRide(UUID rideId) {
        UserContext ctx = getCurrentUserContext();
        UUID userId = ctx.userId();

        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new NotFoundException("Ride not found"));

        if (!ride.getCreatedByUserId().equals(userId)) {
            throw new ForbiddenException("Only the host can cancel this ride");
        }
        if (ride.getStatus() != RideStatus.PENDING
                && ride.getStatus() != RideStatus.ACCEPTED) {
            throw new ConflictException(
                    "Ride is " + ride.getStatus() + " and cannot be cancelled");
        }

        ride.setStatus(RideStatus.CANCELLED);
        ride.setCancelledAt(Instant.now());
        rideRepository.save(ride);
    }

    @Transactional
    public void joinRide(UUID rideId) {
        UserContext ctx = getCurrentUserContext();
        UUID userId = ctx.userId();

        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new NotFoundException("Ride not found"));

        if (ride.getCreatedByUserId().equals(userId)) {
            throw new ForbiddenException("Hosts can't join their own ride");
        }
        if (ride.getStatus() != RideStatus.PENDING
                && ride.getStatus() != RideStatus.ACCEPTED) {
            throw new ConflictException("Ride is " + ride.getStatus());
        }
        if (rideParticipantsRepository.existsByRide_IdAndUserId(rideId, userId)) {
            throw new ConflictException("You've already joined this ride");
        }
        if (ride.getAvailableSeats() <= 0) {
            throw new ConflictException("Ride is full");
        }

        RideParticipants participant = new RideParticipants();
        participant.setRide(ride);
        participant.setUserId(userId);
        rideParticipantsRepository.save(participant);

        // Keep the stored counter in sync; details endpoint also derives
        // seatsAvailable from the participant count, so both stay consistent.
        ride.setAvailableSeats(ride.getAvailableSeats() - 1);
        rideRepository.save(ride);
    }

    @Transactional
    public void leaveRide(UUID rideId) {
        UserContext ctx = getCurrentUserContext();
        UUID userId = ctx.userId();

        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new NotFoundException("Ride not found"));

        long removed = rideParticipantsRepository.deleteByRide_IdAndUserId(rideId, userId);
        if (removed == 0) {
            throw new ConflictException("You haven't joined this ride");
        }

        ride.setAvailableSeats(Math.min(ride.getTotalSeats(), ride.getAvailableSeats() + 1));
        rideRepository.save(ride);
    }

    public List<AvailableRideResponse> getAvailableRides(Double lat, Double lng) {
        UserContext ctx = getCurrentUserContext();
        UUID currentUserId = ctx.userId();

        List<Ride> rides = rideRepository.findAvailableRides(currentUserId);

        Stream<AvailableRideResponse> response = rides.stream()
                .map(r -> toAvailableRideResponse(r, lat, lng));

        // When the rider's location is known, only keep rides whose PICKUP is
        // within SEARCH_RADIUS_KM. Without a location we can't measure distance,
        // so fall back to returning all available rides.
        if (lat != null && lng != null) {
            response = response.filter(a -> a.getDistanceKm() != null
                    && a.getDistanceKm() <= SEARCH_RADIUS_KM);
        }

        return response
                .sorted(Comparator.comparing(
                        AvailableRideResponse::getDistanceKm,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    public List<AvailableRideResponse> getMyRides() {
        UserContext ctx = getCurrentUserContext();
        UUID userId = ctx.userId();

        List<Ride> rides = rideRepository.findMyActiveRides(userId);

        return rides.stream()
                .map(r -> AvailableRideResponse.builder()
                        .id(r.getId().toString())
                        .hostName("You")
                        // Remaining host/* and distance/* fields aren't used
                        // by the _MyRideCard view; leave them null for now.
                        .pickup(r.getPickup() != null ? r.getPickup().getAddress() : null)
                        .drop(r.getDestination() != null ? r.getDestination().getAddress() : null)
                        .pickupLat(r.getPickup() != null ? r.getPickup().getLatitude() : null)
                        .pickupLng(r.getPickup() != null ? r.getPickup().getLongitude() : null)
                        .dropLat(r.getDestination() != null ? r.getDestination().getLatitude() : null)
                        .dropLng(r.getDestination() != null ? r.getDestination().getLongitude() : null)
                        .seatsAvailable(r.getAvailableSeats())
                        .build())
                .toList();
    }

    private AvailableRideResponse toAvailableRideResponse(Ride r, Double lat, Double lng) {
        UUID hostId = r.getCreatedByUserId();
        long trips = rideRepository.countByCreatedByUserIdAndStatus(hostId, RideStatus.COMPLETED);

        Double pickupLat = r.getPickup() != null ? r.getPickup().getLatitude() : null;
        Double pickupLng = r.getPickup() != null ? r.getPickup().getLongitude() : null;
        Double dropLat = r.getDestination() != null ? r.getDestination().getLatitude() : null;
        Double dropLng = r.getDestination() != null ? r.getDestination().getLongitude() : null;

        Double distanceKm = (lat != null && lng != null && pickupLat != null && pickupLng != null)
                ? priceService.distanceKm(lat, lng, pickupLat, pickupLng)
                : null;

        Integer etaMinutes = distanceKm != null
                ? (int) Math.round((distanceKm / 30.0) * 60)
                : null;

        Double fareForRider = priceService.estimateRiderFare(r);

        return AvailableRideResponse.builder()
                .id(r.getId().toString())
                .hostName("Host " + hostId.toString().substring(0, 8))
                .hostRating(null)
                .hostTrips((int) trips)
                .hostGender(r.getGender() != null ? r.getGender().name() : null)
                .etaMinutes(etaMinutes)
                .distanceKm(distanceKm)
                .fareForRider(fareForRider)
                .pickup(r.getPickup() != null ? r.getPickup().getAddress() : null)
                .drop(r.getDestination() != null ? r.getDestination().getAddress() : null)
                .pickupLat(pickupLat)
                .pickupLng(pickupLng)
                .dropLat(dropLat)
                .dropLng(dropLng)
                .seatsAvailable(r.getAvailableSeats())
                .build();
    }

    @Transactional(readOnly = true)
    public RideDetailsResponse getRideDetails(UUID rideId) {
        UserContext ctx = getCurrentUserContext();
        UUID viewerId = ctx.userId();

        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new NotFoundException("Ride not found"));

        UUID hostId = ride.getCreatedByUserId();
        int hostTrips = (int) rideRepository.countByCreatedByUserIdAndStatus(hostId, RideStatus.COMPLETED);

        RideDetailsResponse.HostDto host = RideDetailsResponse.HostDto.builder()
                .id(hostId.toString())
                .name(displayName(hostId))
                .rating(null)
                .trips(hostTrips)
                .gender(ride.getGender() != null ? ride.getGender().name() : null)
                .build();

        List<RideParticipants> joined = ride.getParticipants() != null ? ride.getParticipants() : List.of();

        List<RideDetailsResponse.CoPassengerDto> coPassengers = joined.stream()
                .filter(p -> !p.getUserId().equals(hostId))
                .filter(p -> !p.getUserId().equals(viewerId))
                .map(p -> RideDetailsResponse.CoPassengerDto.builder()
                        .id(p.getUserId().toString())
                        .name(displayName(p.getUserId()))
                        .rating(null)
                        .trips((int) rideRepository.countByCreatedByUserIdAndStatus(p.getUserId(), RideStatus.COMPLETED))
                        .gender(null)
                        .build())
                .toList();

        int seatsBooked = joined.size();
        int seatsAvailable = Math.max(0, ride.getTotalSeats() - seatsBooked);

        RideDetailsResponse.FareDto fare = priceService.computeFare(ride, seatsBooked);

        // Derived from the participants we already loaded above — no extra query.
        boolean youHaveJoined = joined.stream()
                .anyMatch(p -> p.getUserId().equals(viewerId));

        Double pickupLat = ride.getPickup() != null ? ride.getPickup().getLatitude() : null;
        Double pickupLng = ride.getPickup() != null ? ride.getPickup().getLongitude() : null;
        Double dropLat = ride.getDestination() != null ? ride.getDestination().getLatitude() : null;
        Double dropLng = ride.getDestination() != null ? ride.getDestination().getLongitude() : null;

        OffsetDateTime createdAt = ride.getCreatedAt() != null
                ? ride.getCreatedAt().atOffset(ZoneOffset.UTC)
                : null;

        return RideDetailsResponse.builder()
                .id(ride.getId().toString())
                .pickup(ride.getPickup() != null ? ride.getPickup().getAddress() : null)
                .drop(ride.getDestination() != null ? ride.getDestination().getAddress() : null)
                .pickupLat(pickupLat)
                .pickupLng(pickupLng)
                .dropLat(dropLat)
                .dropLng(dropLng)
                .rideType(ride.getRideType() != null ? ride.getRideType().name() : null)
                .status(ride.getStatus() != null ? ride.getStatus().name() : null)
                .createdAt(createdAt)
                .host(host)
                .seatsTotal(ride.getTotalSeats())
                .seatsAvailable(seatsAvailable)
                .coPassengers(coPassengers)
                .fare(fare)
                .youHaveJoined(youHaveJoined)
                .build();
    }

    private static String displayName(UUID userId) {
        return "User " + userId.toString().substring(0, 8);
    }

    public RideStatsResponse getMyStats() {
        UserContext ctx = getCurrentUserContext();
        long trips = "DRIVER".equals(ctx.role())
                ? rideRepository.countByDriverIdAndStatus(ctx.userId(), RideStatus.COMPLETED)
                : rideRepository.countByCreatedByUserIdAndStatus(ctx.userId(), RideStatus.COMPLETED);
        return new RideStatsResponse(trips, null);
    }

    private UserContext getCurrentUserContext() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assert authentication != null;
        return (UserContext) authentication.getDetails();
    }
}