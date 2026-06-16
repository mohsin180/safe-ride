package com.safe_ride.rides_service.service;

import com.safe_ride.rides_service.client.DriverClient;
import com.safe_ride.rides_service.client.DriverSummary;
import com.safe_ride.rides_service.client.PassengerSummary;
import com.safe_ride.rides_service.client.ProfileClient;
import com.safe_ride.rides_service.client.RouteResult;
import com.safe_ride.rides_service.client.RoutingClient;
import com.safe_ride.rides_service.config.UserContext;
import com.safe_ride.rides_service.event.NotificationPublisher;
import com.safe_ride.rides_service.event.RideNotificationEvent;
import com.safe_ride.rides_service.exceptions.ConflictException;
import com.safe_ride.rides_service.exceptions.ForbiddenException;
import com.safe_ride.rides_service.exceptions.NotFoundException;
import com.safe_ride.rides_service.exceptions.RoleNotAllowedException;
import com.safe_ride.rides_service.model.dtos.AvailableRideResponse;
import com.safe_ride.rides_service.model.dtos.CreateRideRequest;
import com.safe_ride.rides_service.model.dtos.DriverEarningsResponse;
import com.safe_ride.rides_service.model.dtos.DriverRideHistoryResponse;
import com.safe_ride.rides_service.model.dtos.JoinRequestBody;
import com.safe_ride.rides_service.model.dtos.PassengerRideHistoryResponse;
import com.safe_ride.rides_service.model.dtos.RideDetailsResponse;
import com.safe_ride.rides_service.model.dtos.RideResponse;
import com.safe_ride.rides_service.model.dtos.RideStatsResponse;
import com.safe_ride.rides_service.model.entity.Gender;
import com.safe_ride.rides_service.model.entity.RatedRole;
import com.safe_ride.rides_service.model.entity.Rating;
import com.safe_ride.rides_service.model.entity.Ride;
import com.safe_ride.rides_service.model.entity.RideParticipants;
import com.safe_ride.rides_service.model.entity.RideStatus;
import com.safe_ride.rides_service.model.mapper.RideMapper;
import com.safe_ride.rides_service.repo.RatingRepository;
import com.safe_ride.rides_service.model.entity.JoinRequest;
import com.safe_ride.rides_service.model.entity.JoinRequestStatus;
import com.safe_ride.rides_service.model.entity.RideDeparture;
import com.safe_ride.rides_service.repo.JoinRequestRepository;
import com.safe_ride.rides_service.repo.RideDepartureRepository;
import com.safe_ride.rides_service.repo.RideParticipantsRepository;
import com.safe_ride.rides_service.repo.RideRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class RideService {
    /** Only rides whose pickup is within this many km of the rider are shown. */
    private static final double SEARCH_RADIUS_KM = 5.0;
    /** Drivers cover more ground, so their feed uses a wider catchment. */
    private static final double DRIVER_SEARCH_RADIUS_KM = 10.0;

    private final RideRepository rideRepository;
    private final RideParticipantsRepository rideParticipantsRepository;
    private final RideDepartureRepository rideDepartureRepository;
    private final JoinRequestRepository joinRequestRepository;
    private final RatingRepository ratingRepository;
    private final RideMapper rideMapper;
    private final PriceService priceService;
    private final ProfileClient profileClient;
    private final DriverClient driverClient;
    private final RoutingClient routingClient;
    private final NotificationPublisher notificationPublisher;

    public RideService(RideRepository rideRepository,
                       RideParticipantsRepository rideParticipantsRepository,
                       RideDepartureRepository rideDepartureRepository,
                       JoinRequestRepository joinRequestRepository,
                       RatingRepository ratingRepository,
                       RideMapper rideMapper,
                       PriceService priceService,
                       ProfileClient profileClient,
                       DriverClient driverClient,
                       RoutingClient routingClient,
                       NotificationPublisher notificationPublisher) {
        this.rideRepository = rideRepository;
        this.rideParticipantsRepository = rideParticipantsRepository;
        this.rideDepartureRepository = rideDepartureRepository;
        this.joinRequestRepository = joinRequestRepository;
        this.ratingRepository = ratingRepository;
        this.rideMapper = rideMapper;
        this.priceService = priceService;
        this.profileClient = profileClient;
        this.driverClient = driverClient;
        this.routingClient = routingClient;
        this.notificationPublisher = notificationPublisher;
    }

    /** host + all current co-passengers of a ride. */
    private List<UUID> hostAndParticipants(Ride ride) {
        List<UUID> ids = new ArrayList<>();
        ids.add(ride.getCreatedByUserId());
        ids.addAll(rideParticipantsRepository.findUserIdsByRideId(ride.getId()));
        return ids;
    }

    public RideResponse createRide(CreateRideRequest request) {
        UserContext ctx = getCurrentUserContext();
        if (!"PASSENGER".equals(ctx.role())) {
            throw new RoleNotAllowedException("Only passengers can request a ride.");
        }

        // One ride at a time: can't create if you already host OR joined one.
        if (rideRepository.hasActiveRideOrJoined(ctx.userId())) {
            throw new ConflictException(
                    "You're already in an active ride — you can only be in one at a time. "
                            + "Leave or cancel it first.");
        }

        Ride ride = rideMapper.toRide(request);
        ride.setCreatedByUserId(ctx.userId());
        ride.setGender(Gender.valueOf(ctx.gender()));
        ride.setStatus(RideStatus.PENDING);

        // Resolve & persist the trip's road distance/duration so pricing is
        // server-authoritative and stable. Prefer the Geoapify routing API;
        // if it's unavailable (no key / network / parse error) fall back to a
        // Haversine straight-line distance and a 30 km/h duration estimate, so
        // every ride still gets a stored distance + duration.
        stampRouteDistance(ride);

        Ride saved = rideRepository.save(ride);
        // Confirmation to the creator that their request is posted.
        notificationPublisher.publish(
                RideNotificationEvent.RIDE_CREATED, saved, List.of(saved.getCreatedByUserId()));
        return rideMapper.toResponse(saved);
    }

    /**
     * Sets {@code routeDistanceKm} / {@code routeDurationMin} on the ride from
     * the routing API, falling back to Haversine (distance) and a 30 km/h
     * estimate (duration) when routing returns nothing or the coords are
     * missing. Never throws — a routing failure must not block ride creation.
     */
    private void stampRouteDistance(Ride ride) {
        if (ride.getPickup() == null || ride.getDestination() == null) {
            return;
        }
        double pickupLat = ride.getPickup().getLatitude();
        double pickupLng = ride.getPickup().getLongitude();
        double dropLat = ride.getDestination().getLatitude();
        double dropLng = ride.getDestination().getLongitude();

        RouteResult route = routingClient
                .route(pickupLat, pickupLng, dropLat, dropLng)
                .orElse(null);

        if (route != null) {
            ride.setRouteDistanceKm(route.distanceKm());
            ride.setRouteDurationMin(route.durationMin());
        } else {
            double haversineKm = priceService.distanceKm(pickupLat, pickupLng, dropLat, dropLng);
            ride.setRouteDistanceKm(haversineKm);
            ride.setRouteDurationMin((int) Math.round((haversineKm / 30.0) * 60));
        }
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

        // Recipients: everyone affected except the host who cancelled.
        List<UUID> recipients = new ArrayList<>(
                rideParticipantsRepository.findUserIdsByRideId(rideId));
        if (ride.getDriverId() != null) {
            recipients.add(ride.getDriverId());
        }

        ride.setStatus(RideStatus.CANCELLED);
        ride.setCancelledAt(Instant.now());
        rideRepository.save(ride);

        notificationPublisher.publish(RideNotificationEvent.RIDE_CANCELLED, ride, recipients);
    }

    /**
     * The assigned driver backs out of a ride. The ride is NOT cancelled —
     * it's re-opened: the driver is un-assigned and the status returns to
     * PENDING so another driver can pick it up. The passengers keep their
     * ride and are notified that we're finding a new driver.
     */
    @Transactional
    public void driverCancelRide(UUID rideId) {
        UserContext ctx = getCurrentUserContext();
        UUID driverId = ctx.userId();

        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new NotFoundException("Ride not found"));

        if (ride.getDriverId() == null || !ride.getDriverId().equals(driverId)) {
            throw new ForbiddenException("Only the assigned driver can drop this ride");
        }
        if (ride.getStatus() != RideStatus.ACCEPTED
                && ride.getStatus() != RideStatus.STARTED) {
            throw new ConflictException(
                    "Ride is " + ride.getStatus() + " and can't be dropped");
        }

        List<UUID> recipients = new ArrayList<>();
        recipients.add(ride.getCreatedByUserId());
        recipients.addAll(rideParticipantsRepository.findUserIdsByRideId(rideId));

        ride.setDriverId(null);
        ride.setStatus(RideStatus.PENDING);
        rideRepository.save(ride);

        notificationPublisher.publish(RideNotificationEvent.DRIVER_CANCELLED, ride, recipients);
    }

    /**
     * A co-passenger asks to join — this does NOT add them yet. It records a
     * PENDING request (with the requester's own route) and notifies the host,
     * who accepts or declines. The actual join happens in
     * {@link #acceptJoinRequest}.
     */
    @Transactional
    public void requestToJoin(UUID rideId, JoinRequestBody body) {
        UserContext ctx = getCurrentUserContext();
        UUID userId = ctx.userId();

        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new NotFoundException("Ride not found"));

        if (ride.getCreatedByUserId().equals(userId)) {
            throw new ForbiddenException("Hosts can't join their own ride");
        }
        if (ride.getStatus() != RideStatus.PENDING) {
            throw new ConflictException(
                    "Ride is " + ride.getStatus() + " and can no longer be joined");
        }
        // Gender-specific matching, enforced server-side: the feed hides
        // opposite-gender rides, but a client could still POST a join with
        // any rideId, so reject a mismatch here too.
        if (ride.getGender() != null && parseGender(ctx.gender()) != ride.getGender()) {
            throw new ForbiddenException(
                    "This ride is for " + ride.getGender().name().toLowerCase()
                            + " passengers only.");
        }
        if (rideParticipantsRepository.existsByRide_IdAndUserId(rideId, userId)) {
            throw new ConflictException("You've already joined this ride");
        }
        if (joinRequestRepository.existsByRideIdAndRequesterIdAndStatus(
                rideId, userId, JoinRequestStatus.PENDING)) {
            throw new ConflictException("You already have a pending request for this ride");
        }
        // One ride at a time: can't request if you already host OR joined another.
        if (rideRepository.hasActiveRideOrJoined(userId)) {
            throw new ConflictException(
                    "You're already in an active ride. Leave it before joining another.");
        }
        if (ride.getAvailableSeats() <= 0) {
            throw new ConflictException("Ride is full");
        }

        // The requester's own route — fall back to the ride's if not supplied.
        JoinRequest request = new JoinRequest();
        request.setRideId(rideId);
        request.setRequesterId(userId);
        request.setPickup(body != null && body.pickup() != null
                ? body.pickup() : addressOf(ride.getPickup()));
        request.setPickupLat(body != null && body.pickupLat() != null
                ? body.pickupLat() : latOf(ride.getPickup()));
        request.setPickupLng(body != null && body.pickupLng() != null
                ? body.pickupLng() : lngOf(ride.getPickup()));
        request.setDrop(body != null && body.drop() != null
                ? body.drop() : addressOf(ride.getDestination()));
        request.setDropLat(body != null && body.dropLat() != null
                ? body.dropLat() : latOf(ride.getDestination()));
        request.setDropLng(body != null && body.dropLng() != null
                ? body.dropLng() : lngOf(ride.getDestination()));
        request.setStatus(JoinRequestStatus.PENDING);
        JoinRequest saved = joinRequestRepository.save(request);

        // Resolve the requester's name + rating for the host's card.
        Map<UUID, PassengerSummary> summaries = fetchPassengerSummaries(List.of(userId));
        String requesterName = resolveName(summaries, userId);
        Double requesterRating = resolveRating(summaries, userId);

        notificationPublisher.publishJoinRequest(
                rideId, List.of(ride.getCreatedByUserId()),
                userId, requesterName, requesterRating, saved.getId(),
                request.getPickup(), request.getDrop());
    }

    /** Host accepts a pending request — the requester now joins the ride. */
    @Transactional
    public void acceptJoinRequest(UUID rideId, UUID requestId) {
        UserContext ctx = getCurrentUserContext();
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new NotFoundException("Ride not found"));
        if (!ride.getCreatedByUserId().equals(ctx.userId())) {
            throw new ForbiddenException("Only the host can respond to join requests");
        }

        JoinRequest request = pendingRequest(requestId, rideId);
        if (ride.getAvailableSeats() <= 0) {
            throw new ConflictException("Ride is full");
        }
        UUID requesterId = request.getRequesterId();

        if (!rideParticipantsRepository.existsByRide_IdAndUserId(rideId, requesterId)) {
            RideParticipants participant = new RideParticipants();
            participant.setRide(ride);
            participant.setUserId(requesterId);
            rideParticipantsRepository.save(participant);
            ride.setAvailableSeats(ride.getAvailableSeats() - 1);
            rideRepository.save(ride);
        }

        request.setStatus(JoinRequestStatus.ACCEPTED);
        joinRequestRepository.save(request);

        // Tell the requester they're in.
        notificationPublisher.publish(
                RideNotificationEvent.JOIN_ACCEPTED, ride, List.of(requesterId));
    }

    /** Host declines a pending request — the requester does not join. */
    @Transactional
    public void declineJoinRequest(UUID rideId, UUID requestId) {
        UserContext ctx = getCurrentUserContext();
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new NotFoundException("Ride not found"));
        if (!ride.getCreatedByUserId().equals(ctx.userId())) {
            throw new ForbiddenException("Only the host can respond to join requests");
        }

        JoinRequest request = pendingRequest(requestId, rideId);
        request.setStatus(JoinRequestStatus.DECLINED);
        joinRequestRepository.save(request);

        notificationPublisher.publish(
                RideNotificationEvent.JOIN_DECLINED, ride, List.of(request.getRequesterId()));
    }

    private JoinRequest pendingRequest(UUID requestId, UUID rideId) {
        JoinRequest request = joinRequestRepository.findById(requestId)
                .orElseThrow(() -> new NotFoundException("Request not found"));
        if (!request.getRideId().equals(rideId)) {
            throw new NotFoundException("Request not found");
        }
        if (request.getStatus() != JoinRequestStatus.PENDING) {
            throw new ConflictException("This request was already " + request.getStatus());
        }
        return request;
    }

    private static String addressOf(com.safe_ride.rides_service.model.entity.Location l) {
        return l != null ? l.getAddress() : null;
    }

    private static Double latOf(com.safe_ride.rides_service.model.entity.Location l) {
        return l != null ? l.getLatitude() : null;
    }

    private static Double lngOf(com.safe_ride.rides_service.model.entity.Location l) {
        return l != null ? l.getLongitude() : null;
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

        // Preserve the fact that this user was on the ride (the participant
        // row is gone) so the bidirectional leave-time ratings can authorize.
        if (!rideDepartureRepository.existsByRideIdAndUserId(rideId, userId)) {
            RideDeparture departure = new RideDeparture();
            departure.setRideId(rideId);
            departure.setUserId(userId);
            rideDepartureRepository.save(departure);
        }

        // Only an in-progress trip is worth rating. If they un-joined a
        // still-pending ride, there's nothing to rate.
        if (ride.getStatus() == RideStatus.ACCEPTED || ride.getStatus() == RideStatus.STARTED) {
            String leaverName = resolveName(fetchPassengerSummaries(List.of(userId)), userId);
            // Prompt the remaining members — host + co-passengers + driver —
            // to rate the one who left.
            List<UUID> recipients = new ArrayList<>();
            recipients.add(ride.getCreatedByUserId());
            recipients.addAll(rideParticipantsRepository.findUserIdsByRideId(rideId));
            if (ride.getDriverId() != null) {
                recipients.add(ride.getDriverId());
            }
            notificationPublisher.publish(
                    RideNotificationEvent.RATE_PROMPT, ride, recipients, null, userId, leaverName);
        }
    }

    public List<AvailableRideResponse> getAvailableRides(Double lat, Double lng) {
        UserContext ctx = getCurrentUserContext();
        UUID currentUserId = ctx.userId();

        // One ride at a time: once you host or join an active ride, there are
        // no other rides to find until you leave/cancel.
        if (rideRepository.hasActiveRideOrJoined(currentUserId)) {
            return List.of();
        }

        // Gender-specific matching: a passenger only sees rides hosted by
        // someone of their own gender. If we can't determine the rider's
        // gender we show nothing rather than risk a cross-gender match.
        Gender riderGender = parseGender(ctx.gender());
        if (riderGender == null) {
            return List.of();
        }

        // With a location, PostGIS does the radius filter AND the nearest-first
        // ordering in one indexed query (ST_DWithin + the <-> KNN operator).
        // Without a location we can't measure distance, so fall back to the
        // non-spatial list (newest-first).
        List<Ride> rides = (lat != null && lng != null)
                ? rideRepository.findAvailableRidesNearby(
                        currentUserId, riderGender.name(), lat, lng, SEARCH_RADIUS_KM * 1000)
                : rideRepository.findAvailableRides(currentUserId, riderGender);

        // Resolve all host names in a single call to profile-service rather
        // than one round-trip per ride.
        Map<UUID, PassengerSummary> hosts = fetchPassengerSummaries(
                rides.stream().map(Ride::getCreatedByUserId).toList());

        // The DB already returned them nearest-first; preserve that order and
        // just attach each ride's distance/fare for display.
        return rides.stream()
                .map(r -> toAvailableRideResponse(r, lat, lng, hosts))
                .toList();
    }

    public List<AvailableRideResponse> getMyRides() {
        UserContext ctx = getCurrentUserContext();
        UUID userId = ctx.userId();

        // Active rides the user is in — whether they host it OR joined it, so
        // a co-passenger's confirmed ride shows here too.
        List<Ride> rides = rideRepository.findMyActiveOrJoinedRides(userId);

        // Resolve host names for rides the user JOINED (their host isn't "You").
        Map<UUID, PassengerSummary> hosts = fetchPassengerSummaries(
                rides.stream()
                        .map(Ride::getCreatedByUserId)
                        .filter(id -> !id.equals(userId))
                        .toList());

        return rides.stream()
                .map(r -> {
                    boolean mine = r.getCreatedByUserId().equals(userId);
                    return AvailableRideResponse.builder()
                            .id(r.getId().toString())
                            .hostName(mine ? "You" : resolveName(hosts, r.getCreatedByUserId()))
                            .youAreHost(mine)
                            .pickup(r.getPickup() != null ? r.getPickup().getAddress() : null)
                            .drop(r.getDestination() != null ? r.getDestination().getAddress() : null)
                            .pickupLat(r.getPickup() != null ? r.getPickup().getLatitude() : null)
                            .pickupLng(r.getPickup() != null ? r.getPickup().getLongitude() : null)
                            .dropLat(r.getDestination() != null ? r.getDestination().getLatitude() : null)
                            .dropLng(r.getDestination() != null ? r.getDestination().getLongitude() : null)
                            .seatsAvailable(r.getAvailableSeats())
                            .build();
                })
                .toList();
    }

    // ── Driver ride-lifecycle ──────────────────────────────────────

    /**
     * Fresh ride requests a driver can claim. With the driver's location,
     * PostGIS bounds the feed to nearby PENDING rides (within
     * {@code DRIVER_SEARCH_RADIUS_KM}), ordered nearest-first and stamped with
     * a distance; without it, falls back to the full pending feed. Reuses the
     * same host-resolution + fare mapping as the passenger feed.
     */
    public List<AvailableRideResponse> getDriverFeed(Double lat, Double lng) {
        requireDriver();
        List<Ride> rides = (lat != null && lng != null)
                ? rideRepository.findDriverFeedNearby(lat, lng, DRIVER_SEARCH_RADIUS_KM * 1000)
                : rideRepository.findDriverFeed();

        Map<UUID, PassengerSummary> hosts = fetchPassengerSummaries(
                rides.stream().map(Ride::getCreatedByUserId).toList());

        // Preserve the DB's nearest-first order; attach distance/fare for display.
        return rides.stream()
                .map(r -> toAvailableRideResponse(r, lat, lng, hosts))
                .toList();
    }

    @Transactional
    public RideResponse acceptRide(UUID rideId) {
        UserContext ctx = requireDriver();
        UUID driverId = ctx.userId();

        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new NotFoundException("Ride not found"));

        if (ride.getCreatedByUserId().equals(driverId)) {
            throw new ForbiddenException("You cannot accept your own ride");
        }
        if (ride.getStatus() != RideStatus.PENDING) {
            throw new ConflictException(
                    "Ride is " + ride.getStatus() + " and can no longer be accepted");
        }
        if (!rideRepository.findDriverActiveRides(driverId).isEmpty()) {
            throw new ConflictException(
                    "Finish your current ride before accepting another");
        }

        ride.setDriverId(driverId);
        ride.setStatus(RideStatus.ACCEPTED);
        Ride saved = rideRepository.save(ride);
        // The host + any co-passengers now have a driver.
        notificationPublisher.publish(
                RideNotificationEvent.RIDE_ACCEPTED, saved, hostAndParticipants(saved));
        return rideMapper.toResponse(saved);
    }

    @Transactional
    public RideResponse startRide(UUID rideId) {
        Ride ride = requireAssignedDriverRide(rideId);
        if (ride.getStatus() != RideStatus.ACCEPTED) {
            throw new ConflictException(
                    "Ride is " + ride.getStatus() + " and cannot be started");
        }
        ride.setStatus(RideStatus.STARTED);
        Ride saved = rideRepository.save(ride);
        notificationPublisher.publish(
                RideNotificationEvent.RIDE_STARTED, saved, hostAndParticipants(saved));
        return rideMapper.toResponse(saved);
    }

    @Transactional
    public RideResponse completeRide(UUID rideId) {
        Ride ride = requireAssignedDriverRide(rideId);
        if (ride.getStatus() != RideStatus.STARTED) {
            throw new ConflictException(
                    "Ride is " + ride.getStatus() + " and cannot be completed");
        }
        ride.setStatus(RideStatus.COMPLETED);
        ride.setCompletedAt(Instant.now());
        Ride saved = rideRepository.save(ride);
        // Host + co-passengers + the driver.
        List<UUID> recipients = hostAndParticipants(saved);
        if (saved.getDriverId() != null) {
            recipients.add(saved.getDriverId());
        }
        notificationPublisher.publish(RideNotificationEvent.RIDE_COMPLETED, saved, recipients);
        return rideMapper.toResponse(saved);
    }

    /** The ride(s) the driver is currently running, rendered with the same
     *  full detail (host, co-passengers, fare) as the ride-details screen.
     *  Transactional so the lazy participant load inside the self-invoked
     *  {@link #getRideDetails} runs with an open session. */
    @Transactional(readOnly = true)
    public List<RideDetailsResponse> getDriverActiveRides() {
        UserContext ctx = requireDriver();
        return rideRepository.findDriverActiveRides(ctx.userId()).stream()
                .map(r -> getRideDetails(r.getId()))
                .toList();
    }

    /** The passenger's in-progress trip (ACCEPTED/STARTED, hosted or joined). */
    @Transactional(readOnly = true)
    public List<RideDetailsResponse> getMyActiveTrip() {
        UserContext ctx = getCurrentUserContext();
        return rideRepository.findMyActiveTrip(ctx.userId()).stream()
                .map(r -> getRideDetails(r.getId()))
                .toList();
    }

    private UserContext requireDriver() {
        UserContext ctx = getCurrentUserContext();
        if (!"DRIVER".equals(ctx.role())) {
            throw new RoleNotAllowedException("Only drivers can perform this action.");
        }
        return ctx;
    }

    /** Loads a ride and asserts the caller is the driver assigned to it. */
    private Ride requireAssignedDriverRide(UUID rideId) {
        UserContext ctx = requireDriver();
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new NotFoundException("Ride not found"));
        if (ride.getDriverId() == null || !ride.getDriverId().equals(ctx.userId())) {
            throw new ForbiddenException("Only the assigned driver can update this ride");
        }
        return ride;
    }

    private AvailableRideResponse toAvailableRideResponse(Ride r, Double lat, Double lng,
                                                          Map<UUID, PassengerSummary> hosts) {
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
                .hostName(resolveName(hosts, hostId))
                .hostRating(resolveRating(hosts, hostId))
                .hostRatingCount((int) ratingRepository.countByRatedId(hostId))
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

        // Load co-passengers via the explicit query rather than the lazy
        // ride.getParticipants() collection — that was coming back empty, so
        // the co-passenger list looked empty even when people had joined.
        // This is the same query the notification fan-out already relies on.
        List<UUID> joinedIds = rideParticipantsRepository.findUserIdsByRideId(rideId);

        // One call to profile-service for the host plus every co-passenger,
        // instead of a lookup per person.
        List<UUID> peopleIds = new ArrayList<>();
        peopleIds.add(hostId);
        peopleIds.addAll(joinedIds);
        Map<UUID, PassengerSummary> people = fetchPassengerSummaries(peopleIds);

        RideDetailsResponse.HostDto host = RideDetailsResponse.HostDto.builder()
                .id(hostId.toString())
                .name(resolveName(people, hostId))
                .rating(resolveRating(people, hostId))
                .ratingCount((int) ratingRepository.countByRatedId(hostId))
                .trips(hostTrips)
                .gender(ride.getGender() != null ? ride.getGender().name() : null)
                .build();

        List<RideDetailsResponse.CoPassengerDto> coPassengers = joinedIds.stream()
                .filter(id -> !id.equals(hostId))
                .filter(id -> !id.equals(viewerId))
                .map(id -> RideDetailsResponse.CoPassengerDto.builder()
                        .id(id.toString())
                        .name(resolveName(people, id))
                        .rating(resolveRating(people, id))
                        .ratingCount((int) ratingRepository.countByRatedId(id))
                        .trips((int) rideRepository.countByCreatedByUserIdAndStatus(id, RideStatus.COMPLETED))
                        .gender(null)
                        .build())
                .toList();

        int seatsBooked = joinedIds.size();
        int seatsAvailable = Math.max(0, ride.getTotalSeats() - seatsBooked);

        RideDetailsResponse.FareDto fare = priceService.computeFare(ride, seatsBooked);

        // Derived from the participant ids we already loaded above.
        boolean youHaveJoined = joinedIds.contains(viewerId);

        Double pickupLat = ride.getPickup() != null ? ride.getPickup().getLatitude() : null;
        Double pickupLng = ride.getPickup() != null ? ride.getPickup().getLongitude() : null;
        Double dropLat = ride.getDestination() != null ? ride.getDestination().getLatitude() : null;
        Double dropLng = ride.getDestination() != null ? ride.getDestination().getLongitude() : null;

        OffsetDateTime createdAt = ride.getCreatedAt() != null
                ? ride.getCreatedAt().atOffset(ZoneOffset.UTC)
                : null;

        // Once a driver has accepted, resolve their profile (name + car +
        // rating + phone) so the passenger active-trip card can show who's
        // driving. Stays null while the ride is still PENDING, or if the
        // driver profile can't be resolved.
        RideDetailsResponse.DriverDto driver = null;
        if (ride.getDriverId() != null) {
            UUID driverId = ride.getDriverId();
            DriverSummary ds = fetchDriverSummaries(List.of(driverId)).get(driverId);
            if (ds != null) {
                driver = RideDetailsResponse.DriverDto.builder()
                        .id(driverId.toString())
                        .name(ds.fullName())
                        .carInfo(ds.carInfo())
                        .rating(ds.rating() != null && ds.rating() > 0.0 ? ds.rating() : null)
                        .phone(ds.phone())
                        .build();
            } else {
                // Profile unreachable — still surface the driver id so the UI
                // knows one is assigned, without fabricating other fields.
                driver = RideDetailsResponse.DriverDto.builder()
                        .id(driverId.toString())
                        .build();
            }
        }

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
                .driver(driver)
                .build();
    }

    /**
     * Batch-resolves passenger userIds to their profile summaries via
     * profile-service. Returns a map keyed by userId. Resilient by design:
     * if profile-service is unreachable or errors, we return an empty map
     * and callers fall back to a placeholder name — names are cosmetic and
     * must never break the ride flow.
     */
    private Map<UUID, PassengerSummary> fetchPassengerSummaries(Collection<UUID> ids) {
        List<UUID> distinct = ids.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (distinct.isEmpty()) {
            return Map.of();
        }
        try {
            List<PassengerSummary> summaries = profileClient.getPassengerSummaries(distinct);
            if (summaries == null) {
                return Map.of();
            }
            return summaries.stream()
                    .filter(s -> s.userId() != null)
                    .collect(Collectors.toMap(PassengerSummary::userId, s -> s, (a, b) -> a));
        } catch (Exception e) {
            return Map.of();
        }
    }

    private static String resolveName(Map<UUID, PassengerSummary> people, UUID userId) {
        PassengerSummary s = people.get(userId);
        if (s != null && s.fullName() != null && !s.fullName().isBlank()) {
            return s.fullName();
        }
        return fallbackName(userId);
    }

    /** A passenger rating only counts once they've actually been rated; a
     *  stored 0.0 means "no ratings yet", which the client renders as "—". */
    private static Double resolveRating(Map<UUID, PassengerSummary> people, UUID userId) {
        PassengerSummary s = people.get(userId);
        if (s == null || s.rating() == null || s.rating() <= 0.0) {
            return null;
        }
        return s.rating();
    }

    private static String fallbackName(UUID userId) {
        return "User " + userId.toString().substring(0, 8);
    }

    public List<PassengerRideHistoryResponse> getMyHistory() {
        UserContext ctx = getCurrentUserContext();
        List<Ride> rides = rideRepository.findMyRideHistory(ctx.userId());

        // Resolve every assigned driver's name + car in a single call so a
        // completed ride can show who drove it.
        Map<UUID, DriverSummary> drivers = fetchDriverSummaries(
                rides.stream().map(Ride::getDriverId).toList());

        // The driver-rating this user already gave on each ride, so the
        // card can show the stars (and the app knows not to re-prompt).
        Map<UUID, Integer> ratingsGiven = fetchDriverRatingsGiven(
                ctx.userId(), rides.stream().map(Ride::getId).toList());

        return rides.stream()
                .map(r -> toHistoryItem(r, drivers, ratingsGiven))
                .toList();
    }

    /** Map of rideId → stars this rater gave the driver, for the rides shown. */
    private Map<UUID, Integer> fetchDriverRatingsGiven(UUID raterId, List<UUID> rideIds) {
        if (rideIds.isEmpty()) {
            return Map.of();
        }
        return ratingRepository
                .findByRaterIdAndRideIdInAndRatedRole(raterId, rideIds, RatedRole.DRIVER)
                .stream()
                .collect(Collectors.toMap(Rating::getRideId, Rating::getStars, (a, b) -> a));
    }

    private PassengerRideHistoryResponse toHistoryItem(Ride r, Map<UUID, DriverSummary> drivers,
                                                       Map<UUID, Integer> ratingsGiven) {
        // Cancelled rides carry a cancelledAt; completed rides have no
        // dedicated end timestamp yet, so fall back to createdAt.
        Instant ts = r.getStatus() == RideStatus.CANCELLED && r.getCancelledAt() != null
                ? r.getCancelledAt()
                : r.getCreatedAt();
        OffsetDateTime completedAt = ts != null ? ts.atOffset(ZoneOffset.UTC) : null;

        DriverSummary driver = r.getDriverId() != null ? drivers.get(r.getDriverId()) : null;
        Integer stars = ratingsGiven.get(r.getId());

        return PassengerRideHistoryResponse.builder()
                .id(r.getId().toString())
                .pickup(r.getPickup() != null ? r.getPickup().getAddress() : null)
                .drop(r.getDestination() != null ? r.getDestination().getAddress() : null)
                .status(r.getStatus() != null ? r.getStatus().name() : null)
                .completedAt(completedAt)
                .fare(priceService.estimateRiderFare(r))
                // Populated for rides a driver actually took; null otherwise
                // (cancelled-before-accept, or driver profile unreachable).
                .driverName(driver != null ? driver.fullName() : null)
                .carInfo(driver != null ? driver.carInfo() : null)
                // The rating this rider gave the driver, or null if not rated.
                .ratingGiven(stars != null ? stars.doubleValue() : null)
                .build();
    }

    /**
     * Batch-resolves driver userIds to their name + vehicle summary via
     * profile-service. Resilient by design, exactly like
     * {@link #fetchPassengerSummaries}: a failure yields an empty map and
     * the history simply shows no driver details rather than erroring.
     */
    private Map<UUID, DriverSummary> fetchDriverSummaries(Collection<UUID> ids) {
        List<UUID> distinct = ids.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (distinct.isEmpty()) {
            return Map.of();
        }
        try {
            List<DriverSummary> summaries = driverClient.getDriverSummaries(distinct);
            if (summaries == null) {
                return Map.of();
            }
            return summaries.stream()
                    .filter(s -> s.userId() != null)
                    .collect(Collectors.toMap(DriverSummary::userId, s -> s, (a, b) -> a));
        } catch (Exception e) {
            return Map.of();
        }
    }

    // ── Driver history + earnings ─────────────────────────────────

    /**
     * The driver's finished rides (COMPLETED + CANCELLED), newest-first, for
     * the driver ride-history screen. Mirrors {@link #getMyHistory} /
     * {@link #toHistoryItem}: one batch call resolves every host name, and each
     * ride is mapped to a {@link DriverRideHistoryResponse}. Driver-gated via
     * the controller's {@code @PreAuthorize}.
     */
    public List<DriverRideHistoryResponse> getDriverHistory() {
        UserContext ctx = requireDriver();
        List<Ride> rides = rideRepository.findDriverRideHistory(ctx.userId());

        // Resolve every host (the passenger who created the ride) in one call.
        Map<UUID, PassengerSummary> hosts = fetchPassengerSummaries(
                rides.stream().map(Ride::getCreatedByUserId).toList());

        return rides.stream()
                .map(r -> toDriverHistoryItem(r, hosts))
                .toList();
    }

    private DriverRideHistoryResponse toDriverHistoryItem(Ride r,
                                                          Map<UUID, PassengerSummary> hosts) {
        // Completed rides carry a completedAt; cancelled rides a cancelledAt.
        // Fall back to createdAt for rides finished before those fields existed.
        Instant ts;
        if (r.getStatus() == RideStatus.COMPLETED && r.getCompletedAt() != null) {
            ts = r.getCompletedAt();
        } else if (r.getStatus() == RideStatus.CANCELLED && r.getCancelledAt() != null) {
            ts = r.getCancelledAt();
        } else {
            ts = r.getCreatedAt();
        }
        OffsetDateTime completedAt = ts != null ? ts.atOffset(ZoneOffset.UTC) : null;

        // The driver's gross for the trip (un-split total). Cancelled rides
        // still report their would-be fare; the frontend shows PKR 0 for
        // cancelled rows itself, but we keep the value honest here.
        Double fare = r.getStatus() == RideStatus.COMPLETED ? priceService.tripFare(r) : 0.0;

        return DriverRideHistoryResponse.builder()
                .id(r.getId().toString())
                .pickup(r.getPickup() != null ? r.getPickup().getAddress() : null)
                .drop(r.getDestination() != null ? r.getDestination().getAddress() : null)
                .status(r.getStatus() != null ? r.getStatus().name() : null)
                .completedAt(completedAt)
                .fare(fare)
                .passengerName(resolveName(hosts, r.getCreatedByUserId()))
                .build();
    }

    /**
     * Driver earnings summary — lifetime totals plus today's. Computed over the
     * driver's COMPLETED rides: {@code tripFare} summed for the lifetime total,
     * and the same restricted to rides whose {@code completedAt} falls on the
     * current UTC date for today's total. Rides completed before the
     * {@code completedAt} column existed have a null timestamp and are
     * therefore excluded from "today".
     */
    public DriverEarningsResponse getDriverEarnings() {
        UserContext ctx = requireDriver();
        List<Ride> completed = rideRepository.findDriverCompletedRides(ctx.userId());

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        double totalEarnings = 0.0;
        int totalTrips = 0;
        double todayEarnings = 0.0;
        int todayTrips = 0;

        for (Ride r : completed) {
            double fare = priceService.tripFare(r);
            totalEarnings += fare;
            totalTrips++;
            if (r.getCompletedAt() != null
                    && r.getCompletedAt().atOffset(ZoneOffset.UTC).toLocalDate().equals(today)) {
                todayEarnings += fare;
                todayTrips++;
            }
        }

        return DriverEarningsResponse.builder()
                .todayEarnings(Math.round(todayEarnings * 100.0) / 100.0)
                .todayTrips(todayTrips)
                .totalEarnings(Math.round(totalEarnings * 100.0) / 100.0)
                .totalTrips(totalTrips)
                .currency("PKR")
                .build();
    }

    public RideStatsResponse getMyStats() {
        UserContext ctx = getCurrentUserContext();
        long trips = "DRIVER".equals(ctx.role())
                ? rideRepository.countByDriverIdAndStatus(ctx.userId(), RideStatus.COMPLETED)
                : rideRepository.countByCreatedByUserIdAndStatus(ctx.userId(), RideStatus.COMPLETED);
        // Average rating this user has received (as driver or passenger),
        // with how many ratings it's based on.
        Double avg = ratingRepository.averageForRatedId(ctx.userId());
        Double rating = avg != null ? Math.round(avg * 10.0) / 10.0 : null;
        long ratingCount = ratingRepository.countByRatedId(ctx.userId());
        return new RideStatsResponse(trips, rating, ratingCount);
    }

    /** Parse the gateway-supplied gender claim, tolerating null/unknown values. */
    private static Gender parseGender(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Gender.valueOf(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private UserContext getCurrentUserContext() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assert authentication != null;
        return (UserContext) authentication.getDetails();
    }
}