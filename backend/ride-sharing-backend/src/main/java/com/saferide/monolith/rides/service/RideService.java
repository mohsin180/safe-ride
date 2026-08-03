package com.saferide.monolith.rides.service;

import com.saferide.monolith.rides.client.DriverClient;
import com.saferide.monolith.rides.client.DriverSummary;
import com.saferide.monolith.rides.client.PassengerSummary;
import com.saferide.monolith.rides.client.ProfileClient;
import com.saferide.monolith.rides.client.RouteResult;
import com.saferide.monolith.rides.client.RoutingClient;
import com.saferide.monolith.rides.config.PricingProperties;
import com.saferide.monolith.common.security.UserContext;
import com.saferide.monolith.rides.model.dtos.CancellationResult;
import com.saferide.monolith.rides.model.dtos.HostAcceptFarePreviewResponse;
import com.saferide.monolith.rides.model.dtos.JoinFarePreviewResponse;
import com.saferide.monolith.rides.model.dtos.PaymentResponse;
import com.saferide.monolith.rides.model.entity.DriverDeclinedRide;
import com.saferide.monolith.rides.model.entity.PaymentStatus;
import com.saferide.monolith.rides.model.entity.RideCancellation;
import com.saferide.monolith.rides.model.entity.RidePayment;
import com.saferide.monolith.rides.model.entity.RideRiderProgress;
import com.saferide.monolith.rides.model.entity.RiderProgressStatus;
import com.saferide.monolith.rides.model.entity.UserBlock;
import com.saferide.monolith.rides.model.entity.UserReport;
import com.saferide.monolith.rides.repo.DriverDeclinedRideRepository;
import com.saferide.monolith.rides.repo.RideCancellationRepository;
import com.saferide.monolith.rides.repo.RidePaymentRepository;
import com.saferide.monolith.rides.repo.RideRiderProgressRepository;
import com.saferide.monolith.rides.repo.UserBlockRepository;
import com.saferide.monolith.rides.repo.UserReportRepository;
import com.saferide.monolith.rides.event.NotificationPublisher;
import com.saferide.monolith.rides.event.RideNotificationEvent;
import com.saferide.monolith.rides.exceptions.ConflictException;
import com.saferide.monolith.rides.exceptions.ForbiddenException;
import com.saferide.monolith.rides.exceptions.NotFoundException;
import com.saferide.monolith.rides.exceptions.RoleNotAllowedException;
import com.saferide.monolith.rides.model.dtos.AvailableRideResponse;
import com.saferide.monolith.rides.model.dtos.CreateRideRequest;
import com.saferide.monolith.rides.model.dtos.DriverEarningsResponse;
import com.saferide.monolith.rides.model.dtos.DriverRideHistoryResponse;
import com.saferide.monolith.rides.model.dtos.JoinRequestBody;
import com.saferide.monolith.rides.model.dtos.PassengerRideHistoryResponse;
import com.saferide.monolith.rides.model.dtos.RideDetailsResponse;
import com.saferide.monolith.rides.model.dtos.RideResponse;
import com.saferide.monolith.rides.model.dtos.RideStatsResponse;
import com.saferide.monolith.rides.model.entity.Gender;
import com.saferide.monolith.rides.model.entity.RatedRole;
import com.saferide.monolith.rides.model.entity.Rating;
import com.saferide.monolith.rides.model.entity.Ride;
import com.saferide.monolith.rides.model.entity.RideParticipants;
import com.saferide.monolith.rides.model.entity.RideStatus;
import com.saferide.monolith.rides.model.mapper.RideMapper;
import com.saferide.monolith.rides.repo.RatingRepository;
import com.saferide.monolith.rides.model.entity.JoinRequest;
import com.saferide.monolith.rides.model.entity.JoinRequestStatus;
import com.saferide.monolith.rides.model.entity.DriverOffer;
import com.saferide.monolith.rides.model.entity.DriverOfferStatus;
import com.saferide.monolith.rides.model.entity.RideDeparture;
import com.saferide.monolith.rides.repo.JoinRequestRepository;
import com.saferide.monolith.rides.repo.DriverOfferRepository;
import com.saferide.monolith.rides.repo.RideDepartureRepository;
import com.saferide.monolith.rides.repo.RideParticipantsRepository;
import com.saferide.monolith.rides.repo.RideRepository;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class RideService {
    /** Only rides whose pickup is within this many km of the rider are shown. */
    private static final double SEARCH_RADIUS_KM = 5.0;
    /** Drivers cover more ground, so their feed uses a wider catchment. */
    private static final double DRIVER_SEARCH_RADIUS_KM = 10.0;
    /** A ride's total seat capacity. The host reserves some at creation; the
     *  rest are what co-passengers can take. No ride exceeds this. */
    private static final int MAX_SEATS = 4;

    private final RideRepository rideRepository;
    private final RideParticipantsRepository rideParticipantsRepository;
    private final RideDepartureRepository rideDepartureRepository;
    private final JoinRequestRepository joinRequestRepository;
    private final DriverOfferRepository driverOfferRepository;
    private final RatingRepository ratingRepository;
    private final RideCancellationRepository rideCancellationRepository;
    private final RidePaymentRepository ridePaymentRepository;
    private final RideRiderProgressRepository rideRiderProgressRepository;
    private final DriverDeclinedRideRepository driverDeclinedRideRepository;
    private final UserReportRepository userReportRepository;
    private final UserBlockRepository userBlockRepository;
    private final RideMapper rideMapper;
    private final PriceService priceService;
    private final PricingProperties pricingProperties;
    private final ProfileClient profileClient;
    private final DriverClient driverClient;
    private final RoutingClient routingClient;
    private final NotificationPublisher notificationPublisher;

    public RideService(RideRepository rideRepository,
                       RideParticipantsRepository rideParticipantsRepository,
                       RideDepartureRepository rideDepartureRepository,
                       JoinRequestRepository joinRequestRepository,
                       DriverOfferRepository driverOfferRepository,
                       RatingRepository ratingRepository,
                       RideCancellationRepository rideCancellationRepository,
                       RidePaymentRepository ridePaymentRepository,
                       RideRiderProgressRepository rideRiderProgressRepository,
                       DriverDeclinedRideRepository driverDeclinedRideRepository,
                       UserReportRepository userReportRepository,
                       UserBlockRepository userBlockRepository,
                       RideMapper rideMapper,
                       PriceService priceService,
                       PricingProperties pricingProperties,
                       ProfileClient profileClient,
                       DriverClient driverClient,
                       RoutingClient routingClient,
                       NotificationPublisher notificationPublisher) {
        this.rideRepository = rideRepository;
        this.rideParticipantsRepository = rideParticipantsRepository;
        this.rideDepartureRepository = rideDepartureRepository;
        this.joinRequestRepository = joinRequestRepository;
        this.driverOfferRepository = driverOfferRepository;
        this.ratingRepository = ratingRepository;
        this.rideCancellationRepository = rideCancellationRepository;
        this.ridePaymentRepository = ridePaymentRepository;
        this.rideRiderProgressRepository = rideRiderProgressRepository;
        this.driverDeclinedRideRepository = driverDeclinedRideRepository;
        this.userReportRepository = userReportRepository;
        this.userBlockRepository = userBlockRepository;
        this.rideMapper = rideMapper;
        this.priceService = priceService;
        this.pricingProperties = pricingProperties;
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
        // The host's pick is how many seats their own party reserves. Capacity
        // is fixed at MAX_SEATS, so the seats left for co-passengers are
        // MAX_SEATS minus what the host took. (request.seats() is 1..4.)
        ride.setTotalSeats(MAX_SEATS);
        ride.setAvailableSeats(Math.max(0, MAX_SEATS - request.seats()));
        // Remember the host's own booking (their party size) so it's shown
        // correctly even after co-passengers join and shrink availableSeats.
        ride.setHostSeats(request.seats());
        // NOT auto-published: the host decides when the ride goes to the
        // driver feed via the "Publish to drivers" button — e.g. after
        // gathering co-passengers first.
        // Scheduled ride: keep a departure time only if it's genuinely in the
        // future; a null or past time means "leave now" (on-demand).
        if (request.departureTime() != null
                && request.departureTime().isAfter(Instant.now())) {
            ride.setDepartureTime(request.departureTime());
        }

        // Resolve & persist the trip's road distance/duration so pricing is
        // server-authoritative and stable. Prefer the Geoapify routing API;
        // if it's unavailable (no key / network / parse error) fall back to a
        // Haversine straight-line distance and a 30 km/h duration estimate, so
        // every ride still gets a stored distance + duration.
        stampRouteDistance(ride);
        // Freeze the host's solo leg as the pricing baseline (their fare is
        // capped at this leg's solo cost, however the shared route grows).
        ride.setHostLegKm(ride.getRouteDistanceKm());
        ride.setHostLegMin(ride.getRouteDurationMin());

        Ride saved = rideRepository.save(ride);
        // Confirmation to the creator that their request is posted.
        notificationPublisher.publish(
                RideNotificationEvent.RIDE_CREATED, saved, List.of(saved.getCreatedByUserId()));
        return rideMapper.toResponse(saved);
    }

    /**
     * Host publishes their ride to the driver feed. The ride keeps gathering
     * co-passengers as before; this just makes it visible to drivers (even if
     * its passenger seats are already full — a full group still needs a
     * driver). Host-only, and only while the ride is still PENDING.
     */
    @Transactional
    public RideResponse publishRide(UUID rideId) {
        UserContext ctx = getCurrentUserContext();
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new NotFoundException("Ride not found"));
        if (!ride.getCreatedByUserId().equals(ctx.userId())) {
            throw new ForbiddenException("Only the host can publish this ride");
        }
        if (ride.getStatus() != RideStatus.PENDING) {
            throw new ConflictException(
                    "Ride is " + ride.getStatus() + " and can no longer be published");
        }
        ride.setPublishedToDrivers(true);
        Ride saved = rideRepository.save(ride);
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

    /**
     * Recompute + persist the FULL shared-route distance/duration through every
     * rider's stop (host + each accepted co-passenger's pickup/drop), so the
     * trip length — and therefore the fare — grows with each co-passenger's
     * detour. Ordered all-pickups-then-all-drops via nearest-neighbour, so
     * everyone is picked up before anyone is dropped and pickup precedes drop.
     * Falls back to a Haversine sum through the ordered stops if routing is
     * unavailable. Called whenever the rider set changes (join / leave).
     */
    private void restampFullRoute(Ride ride) {
        if (ride.getPickup() == null || ride.getDestination() == null) {
            return;
        }
        List<double[]> pickups = new ArrayList<>();
        List<double[]> drops = new ArrayList<>();
        pickups.add(new double[]{ride.getPickup().getLatitude(),
                ride.getPickup().getLongitude()});
        drops.add(new double[]{ride.getDestination().getLatitude(),
                ride.getDestination().getLongitude()});

        List<UUID> joined = rideParticipantsRepository.findUserIdsByRideId(ride.getId());
        if (!joined.isEmpty()) {
            Map<UUID, JoinRequest> accepted = joinRequestRepository
                    .findByRideIdAndStatus(ride.getId(), JoinRequestStatus.ACCEPTED)
                    .stream()
                    .collect(Collectors.toMap(JoinRequest::getRequesterId, jr -> jr, (a, b) -> a));
            for (UUID riderId : joined) {
                JoinRequest jr = accepted.get(riderId);
                if (jr == null) continue;
                if (jr.getPickupLat() != null && jr.getPickupLng() != null) {
                    pickups.add(new double[]{jr.getPickupLat(), jr.getPickupLng()});
                }
                if (jr.getDropLat() != null && jr.getDropLng() != null) {
                    drops.add(new double[]{jr.getDropLat(), jr.getDropLng()});
                }
            }
        }

        // Order: start at the host pickup, nearest-neighbour through the other
        // pickups, then nearest-neighbour through the drops.
        List<double[]> ordered = new ArrayList<>();
        double[] cursor = pickups.get(0);
        ordered.add(cursor);
        List<double[]> remainingPickups = new ArrayList<>(pickups.subList(1, pickups.size()));
        cursor = drainNearest(cursor, remainingPickups, ordered);
        List<double[]> remainingDrops = new ArrayList<>(drops);
        drainNearest(cursor, remainingDrops, ordered);

        RouteResult route = routingClient.routeThrough(ordered).orElse(null);
        if (route != null) {
            ride.setRouteDistanceKm(route.distanceKm());
            ride.setRouteDurationMin(route.durationMin());
        } else {
            double km = 0.0;
            for (int i = 1; i < ordered.size(); i++) {
                km += priceService.distanceKm(ordered.get(i - 1)[0], ordered.get(i - 1)[1],
                        ordered.get(i)[0], ordered.get(i)[1]);
            }
            ride.setRouteDistanceKm(Math.round(km * 100.0) / 100.0);
            ride.setRouteDurationMin((int) Math.round((km / 30.0) * 60));
        }
    }

    /**
     * Simulated trip metrics {@code [km, minutes]} for this ride's current
     * stops PLUS an optional candidate rider's pickup/drop — same
     * pickups-then-drops nearest-neighbour ordering as {@link #restampFullRoute},
     * but Haversine-only (no routing API), so previews are instant and free.
     */
    private double[] simulatedRouteMetrics(Ride ride, Double addPickupLat,
            Double addPickupLng, Double addDropLat, Double addDropLng) {
        List<double[]> pickups = new ArrayList<>();
        List<double[]> drops = new ArrayList<>();
        pickups.add(new double[]{ride.getPickup().getLatitude(),
                ride.getPickup().getLongitude()});
        drops.add(new double[]{ride.getDestination().getLatitude(),
                ride.getDestination().getLongitude()});
        Map<UUID, JoinRequest> accepted = joinRequestRepository
                .findByRideIdAndStatus(ride.getId(), JoinRequestStatus.ACCEPTED)
                .stream()
                .collect(Collectors.toMap(JoinRequest::getRequesterId, jr -> jr, (a, b) -> a));
        for (UUID riderId : rideParticipantsRepository.findUserIdsByRideId(ride.getId())) {
            JoinRequest jr = accepted.get(riderId);
            if (jr == null) continue;
            if (jr.getPickupLat() != null && jr.getPickupLng() != null) {
                pickups.add(new double[]{jr.getPickupLat(), jr.getPickupLng()});
            }
            if (jr.getDropLat() != null && jr.getDropLng() != null) {
                drops.add(new double[]{jr.getDropLat(), jr.getDropLng()});
            }
        }
        if (addPickupLat != null && addPickupLng != null) {
            pickups.add(new double[]{addPickupLat, addPickupLng});
        }
        if (addDropLat != null && addDropLng != null) {
            drops.add(new double[]{addDropLat, addDropLng});
        }
        List<double[]> ordered = new ArrayList<>();
        double[] cursor = pickups.get(0);
        ordered.add(cursor);
        cursor = drainNearest(cursor, new ArrayList<>(pickups.subList(1, pickups.size())), ordered);
        drainNearest(cursor, new ArrayList<>(drops), ordered);
        double km = 0.0;
        for (int i = 1; i < ordered.size(); i++) {
            km += priceService.distanceKm(ordered.get(i - 1)[0], ordered.get(i - 1)[1],
                    ordered.get(i)[0], ordered.get(i)[1]);
        }
        return new double[]{km, (km / 30.0) * 60.0};
    }

    /** Sentinel key for the candidate rider inside a shares preview. */
    private static final UUID PREVIEW_CANDIDATE = new UUID(0L, 0L);

    /** What would a rider with this route + seats pay if they joined? */
    @Transactional(readOnly = true)
    public JoinFarePreviewResponse previewJoinFare(UUID rideId, double pickupLat,
            double pickupLng, double dropLat, double dropLng, int seats) {
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new NotFoundException("Ride not found"));
        if (ride.getPickup() == null || ride.getDestination() == null) {
            throw new ConflictException("Ride has no route to price");
        }
        double[] m = simulatedRouteMetrics(ride, pickupLat, pickupLng, dropLat, dropLng);
        double gross = Math.round(priceService.grossFromMetrics(
                m[0], m[1], ride.getRideType()) * 100.0) / 100.0;
        double leg = priceService.distanceKm(pickupLat, pickupLng, dropLat, dropLng);
        Map<UUID, Double> shares = priceService.previewShares(
                ride, PREVIEW_CANDIDATE, leg, seats, gross);
        return new JoinFarePreviewResponse(
                shares.getOrDefault(PREVIEW_CANDIDATE, 0.0),
                shares.getOrDefault(ride.getCreatedByUserId(), 0.0),
                gross, Math.round(m[0] * 10.0) / 10.0, (int) Math.round(m[1]),
                pricingProperties.getCurrency());
    }

    /** The host's before/after fare picture for a pending join request. */
    @Transactional(readOnly = true)
    public HostAcceptFarePreviewResponse previewAcceptFare(UUID rideId, UUID requestId) {
        UserContext ctx = getCurrentUserContext();
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new NotFoundException("Ride not found"));
        if (!ride.getCreatedByUserId().equals(ctx.userId())) {
            throw new ForbiddenException("Only the host can preview join requests");
        }
        JoinRequest jr = joinRequestRepository.findById(requestId)
                .orElseThrow(() -> new NotFoundException("Request not found"));
        if (!jr.getRideId().equals(rideId)) {
            throw new NotFoundException("Request not found");
        }
        double now = priceService.computeShares(ride)
                .getOrDefault(ride.getCreatedByUserId(), 0.0);
        // Missing requester coords → neutral preview on the current route.
        Double pLat = jr.getPickupLat(); Double pLng = jr.getPickupLng();
        Double dLat = jr.getDropLat(); Double dLng = jr.getDropLng();
        double[] m = simulatedRouteMetrics(ride, pLat, pLng, dLat, dLng);
        double gross = Math.round(priceService.grossFromMetrics(
                m[0], m[1], ride.getRideType()) * 100.0) / 100.0;
        double leg = (pLat != null && pLng != null && dLat != null && dLng != null)
                ? priceService.distanceKm(pLat, pLng, dLat, dLng)
                : 1.0;
        int seats = jr.getSeats() != null && jr.getSeats() > 0 ? jr.getSeats() : 1;
        Map<UUID, Double> shares = priceService.previewShares(
                ride, PREVIEW_CANDIDATE, leg, seats, gross);
        return new HostAcceptFarePreviewResponse(
                now,
                shares.getOrDefault(ride.getCreatedByUserId(), 0.0),
                shares.getOrDefault(PREVIEW_CANDIDATE, 0.0),
                gross, Math.round(m[0] * 10.0) / 10.0, (int) Math.round(m[1]),
                pricingProperties.getCurrency());
    }

    /** Greedily append the nearest remaining point to {@code out} until the
     *  pool is empty; returns the final cursor position. */
    private double[] drainNearest(double[] from, List<double[]> pool, List<double[]> out) {
        double[] cursor = from;
        while (!pool.isEmpty()) {
            int best = 0;
            double bestD = Double.MAX_VALUE;
            for (int i = 0; i < pool.size(); i++) {
                double d = priceService.distanceKm(cursor[0], cursor[1],
                        pool.get(i)[0], pool.get(i)[1]);
                if (d < bestD) { bestD = d; best = i; }
            }
            cursor = pool.remove(best);
            out.add(cursor);
        }
        return cursor;
    }

    @Transactional
    public CancellationResult cancelRide(UUID rideId, String reason) {
        UserContext ctx = getCurrentUserContext();
        UUID userId = ctx.userId();

        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new NotFoundException("Ride not found"));

        if (!ride.getCreatedByUserId().equals(userId)) {
            throw new ForbiddenException("Only the host can cancel this ride");
        }
        if (ride.getStatus() != RideStatus.PENDING
                && ride.getStatus() != RideStatus.ACCEPTED
                && ride.getStatus() != RideStatus.ARRIVED) {
            throw new ConflictException(
                    "Ride is " + ride.getStatus() + " and cannot be cancelled");
        }

        // Cancelling is free until the driver has actually reached the pickup
        // (ARRIVED). After that a flat fee is recorded against the rider.
        double fee = ride.getStatus() == RideStatus.ARRIVED
                ? pricingProperties.getCancellationFee()
                : 0.0;
        String currency = pricingProperties.getCurrency();

        // Recipients: everyone affected except the host who cancelled.
        List<UUID> recipients = new ArrayList<>(
                rideParticipantsRepository.findUserIdsByRideId(rideId));
        if (ride.getDriverId() != null) {
            recipients.add(ride.getDriverId());
        }

        ride.setStatus(RideStatus.CANCELLED);
        ride.setCancelledAt(Instant.now());
        rideRepository.save(ride);

        // Record the cancellation (fee + reason) — kept for strike counts and
        // later fee settlement once payments exist.
        RideCancellation record = new RideCancellation();
        record.setRideId(rideId);
        record.setUserId(userId);
        record.setFee(fee);
        record.setCurrency(currency);
        record.setReason(reason != null && !reason.isBlank()
                ? reason.trim() : null);
        rideCancellationRepository.save(record);

        notificationPublisher.publish(RideNotificationEvent.RIDE_CANCELLED, ride, recipients);

        long strikes = rideCancellationRepository
                .countByUserIdAndFeeGreaterThan(userId, 0.0);
        return new CancellationResult(fee, currency, strikes);
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
        // Only before the trip is moving — once STARTED, the fare ledger and
        // per-rider progress exist, so re-opening the ride would orphan them.
        if (ride.getStatus() != RideStatus.ACCEPTED
                && ride.getStatus() != RideStatus.ARRIVED) {
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
        // How many seats the rider wants (default 1), capped by what's left.
        int wantedSeats = body != null && body.seats() != null && body.seats() > 0
                ? body.seats() : 1;
        if (wantedSeats > ride.getAvailableSeats()) {
            throw new ConflictException(
                    "Only " + ride.getAvailableSeats() + " seat(s) left on this ride");
        }

        // The requester's own route — fall back to the ride's if not supplied.
        JoinRequest request = new JoinRequest();
        request.setRideId(rideId);
        request.setRequesterId(userId);
        request.setSeats(wantedSeats);
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
        int wantedSeats = request.getSeats() != null && request.getSeats() > 0
                ? request.getSeats() : 1;
        if (ride.getAvailableSeats() < wantedSeats) {
            throw new ConflictException(
                    "Only " + ride.getAvailableSeats() + " seat(s) left on this ride");
        }
        UUID requesterId = request.getRequesterId();

        if (!rideParticipantsRepository.existsByRide_IdAndUserId(rideId, requesterId)) {
            RideParticipants participant = new RideParticipants();
            participant.setRide(ride);
            participant.setUserId(requesterId);
            participant.setSeats(wantedSeats);
            rideParticipantsRepository.save(participant);
            ride.setAvailableSeats(ride.getAvailableSeats() - wantedSeats);
            rideRepository.save(ride);
        }

        request.setStatus(JoinRequestStatus.ACCEPTED);
        joinRequestRepository.save(request);

        // The rider set changed — recompute the full multi-stop route so the
        // trip distance/time (and thus the fare) reflect this rider's detour.
        restampFullRoute(ride);
        rideRepository.save(ride);

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

    private static String addressOf(com.saferide.monolith.rides.model.entity.Location l) {
        return l != null ? l.getAddress() : null;
    }

    private static Double latOf(com.saferide.monolith.rides.model.entity.Location l) {
        return l != null ? l.getLatitude() : null;
    }

    private static Double lngOf(com.saferide.monolith.rides.model.entity.Location l) {
        return l != null ? l.getLongitude() : null;
    }

    @Transactional
    public void leaveRide(UUID rideId) {
        UserContext ctx = getCurrentUserContext();
        UUID userId = ctx.userId();

        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new NotFoundException("Ride not found"));

        RideParticipants participant = rideParticipantsRepository
                .findByRide_IdAndUserId(rideId, userId)
                .orElseThrow(() -> new ConflictException("You haven't joined this ride"));
        int freed = participant.getSeats() > 0 ? participant.getSeats() : 1;
        rideParticipantsRepository.delete(participant);
        rideParticipantsRepository.flush(); // so the route recompute below sees them gone

        // Return the seats this rider was holding back to the pool.
        ride.setAvailableSeats(
                Math.min(ride.getTotalSeats(), ride.getAvailableSeats() + freed));
        // Rider left — shrink the shared route back so the fare drops again.
        restampFullRoute(ride);
        rideRepository.save(ride);

        // If the trip's cash ledger was already opened (ride STARTED), remove
        // the leaver's un-collected payment + progress row — otherwise their
        // never-paid share would be auto-"collected" at complete and inflate
        // the driver's earnings with phantom cash.
        ridePaymentRepository.findByRideIdAndUserId(rideId, userId).ifPresent(p -> {
            if (p.getStatus() != PaymentStatus.COLLECTED) {
                ridePaymentRepository.delete(p);
            }
        });
        rideRiderProgressRepository.findByRideIdAndUserId(rideId, userId)
                .ifPresent(rideRiderProgressRepository::delete);

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
        return getAvailableRides(lat, lng, null, null, null);
    }

    public List<AvailableRideResponse> getAvailableRides(
            Double lat, Double lng, Double dropLat, Double dropLng) {
        return getAvailableRides(lat, lng, dropLat, dropLng, null);
    }

    /** How far a ride's drop-off may be from the rider's own destination and
     *  still count as "on the way" (km). */
    private static final double DROP_MATCH_RADIUS_KM = 8.0;

    public List<AvailableRideResponse> getAvailableRides(
            Double lat, Double lng, Double dropLat, Double dropLng, Integer seats) {
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

        // Keep blocked users apart: hide rides hosted by someone the viewer has
        // blocked, or who has blocked the viewer.
        Set<UUID> blocked = blockedRelatedTo(currentUserId);
        if (!blocked.isEmpty()) {
            rides = rides.stream()
                    .filter(r -> !blocked.contains(r.getCreatedByUserId()))
                    .toList();
        }

        // Route-overlap: when the rider gave a destination, keep only rides
        // whose drop-off is roughly on the way (within DROP_MATCH_RADIUS_KM of
        // the rider's own drop) and rank them by combined pickup + drop
        // closeness — a better "is this rider going my way?" than pickup alone.
        if (dropLat != null && dropLng != null && lat != null && lng != null) {
            rides = rides.stream()
                    .filter(r -> r.getDestination() != null)
                    .map(r -> Map.entry(r, dropDistanceKm(r, dropLat, dropLng)))
                    .filter(e -> e.getValue() <= DROP_MATCH_RADIUS_KM)
                    .sorted(java.util.Comparator.comparingDouble(e ->
                            pickupDistanceKm(e.getKey(), lat, lng) + e.getValue()))
                    .map(Map.Entry::getKey)
                    .toList();
        }

        // Resolve all host names in a single call to profile-service rather
        // than one round-trip per ride.
        Map<UUID, PassengerSummary> hosts = fetchPassengerSummaries(
                rides.stream().map(Ride::getCreatedByUserId).toList());

        // The DB already returned them nearest-first; preserve that order and
        // just attach each ride's distance/fare for display. When we know the
        // searcher's route + seats, "Your fare" becomes the TRUE weighted
        // preview (their detour + headcount + seats), not the current split.
        int searcherSeats = seats != null && seats > 0 ? seats : 1;
        return rides.stream()
                .map(r -> {
                    AvailableRideResponse resp = toAvailableRideResponse(r, lat, lng, hosts);
                    if (lat != null && lng != null && dropLat != null && dropLng != null
                            && r.getPickup() != null && r.getDestination() != null) {
                        try {
                            JoinFarePreviewResponse p = previewJoinFare(
                                    r.getId(), lat, lng, dropLat, dropLng, searcherSeats);
                            resp.setFareForRider(Math.round(p.yourShare() * 100.0) / 100.0);
                        } catch (Exception ignored) {
                            // keep the rough estimate on any preview failure
                        }
                    }
                    return resp;
                })
                .toList();
    }

    public List<AvailableRideResponse> getMyRides() {
        UserContext ctx = getCurrentUserContext();
        UUID userId = ctx.userId();

        // Active rides the user is in — whether they host it OR joined it, so
        // a co-passenger's confirmed ride shows here too.
        List<Ride> rides = rideRepository.findMyActiveOrJoinedRides(userId);

        // Resolve summaries (name + rating) for every host, including the
        // current user for their own rides — so the host card shows real
        // rating/trips instead of dashes. hostName is still "You" for mine.
        Map<UUID, PassengerSummary> hosts = fetchPassengerSummaries(
                rides.stream()
                        .map(Ride::getCreatedByUserId)
                        .toList());

        return rides.stream()
                .map(r -> {
                    UUID hostId = r.getCreatedByUserId();
                    boolean mine = hostId.equals(userId);
                    return AvailableRideResponse.builder()
                            .id(r.getId().toString())
                            .hostName(mine ? "You" : resolveName(hosts, hostId))
                            .hostRating(resolveRating(hosts, hostId))
                            .hostRatingCount((int) ratingRepository.countByRatedId(hostId))
                            .hostTrips((int) rideRepository
                                    .countCompletedTripsForUser(hostId))
                            .hostGender(r.getGender() != null ? r.getGender().name() : null)
                            .youAreHost(mine)
                            .fareForRider(priceService.estimateRiderFare(r))
                            .pickup(r.getPickup() != null ? r.getPickup().getAddress() : null)
                            .drop(r.getDestination() != null ? r.getDestination().getAddress() : null)
                            .pickupLat(r.getPickup() != null ? r.getPickup().getLatitude() : null)
                            .pickupLng(r.getPickup() != null ? r.getPickup().getLongitude() : null)
                            .dropLat(r.getDestination() != null ? r.getDestination().getLatitude() : null)
                            .dropLng(r.getDestination() != null ? r.getDestination().getLongitude() : null)
                            .seatsAvailable(r.getAvailableSeats())
                            .ridersJoined((int) rideParticipantsRepository.countByRide_Id(r.getId()))
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
        UserContext ctx = requireDriver();
        List<Ride> rides = (lat != null && lng != null)
                ? rideRepository.findDriverFeedNearby(lat, lng, DRIVER_SEARCH_RADIUS_KM * 1000)
                : rideRepository.findDriverFeed();

        // Hide rides this driver has dismissed so they don't reappear each poll.
        Set<UUID> declined = new HashSet<>(
                driverDeclinedRideRepository.findRideIdsByDriverId(ctx.userId()));
        if (!declined.isEmpty()) {
            rides = rides.stream()
                    .filter(r -> !declined.contains(r.getId()))
                    .toList();
        }

        // Keep blocked users apart — hide rides hosted by anyone in a block
        // relationship with this driver.
        Set<UUID> blocked = blockedRelatedTo(ctx.userId());
        if (!blocked.isEmpty()) {
            rides = rides.stream()
                    .filter(r -> !blocked.contains(r.getCreatedByUserId()))
                    .toList();
        }

        Map<UUID, PassengerSummary> hosts = fetchPassengerSummaries(
                rides.stream().map(Ride::getCreatedByUserId).toList());

        // Preserve the DB's nearest-first order; attach distance/fare for display.
        return rides.stream()
                .map(r -> toAvailableRideResponse(r, lat, lng, hosts))
                .toList();
    }

    /** The driver dismisses a ride from their feed — recorded so it stays
     *  hidden for them on later polls. Other drivers still see it. */
    @Transactional
    public void declineRideFromFeed(UUID rideId) {
        UserContext ctx = requireDriver();
        if (!driverDeclinedRideRepository
                .existsByDriverIdAndRideId(ctx.userId(), rideId)) {
            DriverDeclinedRide d = new DriverDeclinedRide();
            d.setDriverId(ctx.userId());
            d.setRideId(rideId);
            driverDeclinedRideRepository.save(d);
        }
    }

    /** Record a misconduct report against another user on a ride. Both the
     *  reporter and the reported party must actually be on the ride. */
    @Transactional
    public void reportUser(UUID rideId, UUID reportedId, String reason) {
        UUID reporterId = getCurrentUserContext().userId();
        if (reporterId.equals(reportedId)) {
            throw new ConflictException("You can't report yourself");
        }
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new NotFoundException("Ride not found"));
        if (!isMemberOrDriver(ride, reporterId)
                || !isMemberOrDriver(ride, reportedId)) {
            throw new ForbiddenException("Both users must be on this ride");
        }
        UserReport report = new UserReport();
        report.setRideId(rideId);
        report.setReporterId(reporterId);
        report.setReportedId(reportedId);
        report.setReason(reason != null && !reason.isBlank() ? reason.trim() : null);
        userReportRepository.save(report);
    }

    /** Block another user — neither will see the other's rides in matching. */
    @Transactional
    public void blockUser(UUID blockedId) {
        UUID blockerId = getCurrentUserContext().userId();
        if (blockerId.equals(blockedId)) {
            throw new ConflictException("You can't block yourself");
        }
        if (!userBlockRepository.existsByBlockerIdAndBlockedId(blockerId, blockedId)) {
            UserBlock b = new UserBlock();
            b.setBlockerId(blockerId);
            b.setBlockedId(blockedId);
            userBlockRepository.save(b);
        }
    }

    /** Users to keep out of this user's matching (blocked, either direction). */
    private Set<UUID> blockedRelatedTo(UUID userId) {
        Set<UUID> ids = new HashSet<>(userBlockRepository.findBlockedBy(userId));
        ids.addAll(userBlockRepository.findBlockersOf(userId));
        return ids;
    }

    /**
     * A driver OFFERS to drive — this does NOT assign them. It records a
     * PENDING {@link DriverOffer} and notifies the host, who accepts (which
     * assigns the driver via {@link #acceptDriverOffer}) or declines. Replaces
     * the old instant-accept so the host always approves their driver.
     */
    @Transactional
    public void offerToDrive(UUID rideId) {
        UserContext ctx = requireDriver();
        UUID driverId = ctx.userId();

        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new NotFoundException("Ride not found"));

        if (ride.getCreatedByUserId().equals(driverId)) {
            throw new ForbiddenException("You cannot offer to drive your own ride");
        }
        if (ride.getStatus() != RideStatus.PENDING) {
            throw new ConflictException(
                    "Ride is " + ride.getStatus() + " and no longer needs a driver");
        }
        if (ride.getDriverId() != null) {
            throw new ConflictException("This ride already has a driver");
        }
        if (!rideRepository.findDriverActiveRides(driverId).isEmpty()) {
            throw new ConflictException(
                    "Finish your current ride before offering on another");
        }
        if (driverOfferRepository.existsByRideIdAndDriverIdAndStatus(
                rideId, driverId, DriverOfferStatus.PENDING)) {
            throw new ConflictException("You already offered to drive this ride");
        }

        DriverOffer offer = new DriverOffer();
        offer.setRideId(rideId);
        offer.setDriverId(driverId);
        offer.setStatus(DriverOfferStatus.PENDING);
        DriverOffer saved = driverOfferRepository.save(offer);

        // Resolve the driver's name + rating for the host's card.
        DriverSummary summary = fetchDriverSummaries(List.of(driverId)).get(driverId);
        String driverName = summary != null ? summary.fullName() : "A driver";
        Double driverRating = summary != null ? summary.rating() : null;

        notificationPublisher.publishDriverOffer(
                rideId, List.of(ride.getCreatedByUserId()),
                driverId, driverName, driverRating, saved.getId(),
                addressOf(ride.getPickup()), addressOf(ride.getDestination()));
    }

    /**
     * Host accepts a driver's offer — the driver is assigned and the ride
     * moves to ACCEPTED. Other pending offers are declined. Host-only.
     */
    @Transactional
    public RideResponse acceptDriverOffer(UUID rideId, UUID offerId) {
        UserContext ctx = getCurrentUserContext();
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new NotFoundException("Ride not found"));
        if (!ride.getCreatedByUserId().equals(ctx.userId())) {
            throw new ForbiddenException("Only the host can respond to driver offers");
        }
        if (ride.getStatus() != RideStatus.PENDING) {
            throw new ConflictException(
                    "Ride is " + ride.getStatus() + " and can no longer take a driver");
        }
        if (ride.getDriverId() != null) {
            throw new ConflictException("This ride already has a driver");
        }

        DriverOffer offer = pendingOffer(offerId, rideId);
        UUID driverId = offer.getDriverId();

        ride.setDriverId(driverId);
        ride.setStatus(RideStatus.ACCEPTED);
        Ride saved = rideRepository.save(ride);

        offer.setStatus(DriverOfferStatus.ACCEPTED);
        driverOfferRepository.save(offer);

        // Any other pending offers on this ride are now moot — decline them
        // and let those drivers know.
        for (DriverOffer other : driverOfferRepository.findByRideIdAndStatus(
                rideId, DriverOfferStatus.PENDING)) {
            other.setStatus(DriverOfferStatus.DECLINED);
            driverOfferRepository.save(other);
            notificationPublisher.publish(
                    RideNotificationEvent.DRIVER_OFFER_DECLINED, saved,
                    List.of(other.getDriverId()));
        }

        // The chosen driver is on; host + co-passengers now have a driver.
        notificationPublisher.publish(
                RideNotificationEvent.DRIVER_OFFER_ACCEPTED, saved, List.of(driverId));
        notificationPublisher.publish(
                RideNotificationEvent.RIDE_ACCEPTED, saved, hostAndParticipants(saved));
        return rideMapper.toResponse(saved);
    }

    /** Host declines a driver's offer — the driver is not assigned. */
    @Transactional
    public void declineDriverOffer(UUID rideId, UUID offerId) {
        UserContext ctx = getCurrentUserContext();
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new NotFoundException("Ride not found"));
        if (!ride.getCreatedByUserId().equals(ctx.userId())) {
            throw new ForbiddenException("Only the host can respond to driver offers");
        }
        DriverOffer offer = pendingOffer(offerId, rideId);
        offer.setStatus(DriverOfferStatus.DECLINED);
        driverOfferRepository.save(offer);
        notificationPublisher.publish(
                RideNotificationEvent.DRIVER_OFFER_DECLINED, ride, List.of(offer.getDriverId()));
    }

    private DriverOffer pendingOffer(UUID offerId, UUID rideId) {
        DriverOffer offer = driverOfferRepository.findById(offerId)
                .orElseThrow(() -> new NotFoundException("Offer not found"));
        if (!offer.getRideId().equals(rideId)) {
            throw new NotFoundException("Offer not found");
        }
        if (offer.getStatus() != DriverOfferStatus.PENDING) {
            throw new ConflictException("This offer was already " + offer.getStatus());
        }
        return offer;
    }

    /** The assigned driver signals they've reached the pickup point
     *  (ACCEPTED → ARRIVED). Lets every rider's screen flip to "driver has
     *  arrived" from the same source of truth. */
    @Transactional
    public RideResponse arriveRide(UUID rideId) {
        Ride ride = requireAssignedDriverRide(rideId);
        if (ride.getStatus() != RideStatus.ACCEPTED) {
            throw new ConflictException(
                    "Ride is " + ride.getStatus() + " and cannot be marked arrived");
        }
        ride.setStatus(RideStatus.ARRIVED);
        Ride saved = rideRepository.save(ride);
        notificationPublisher.publish(
                RideNotificationEvent.RIDE_ARRIVED, saved, hostAndParticipants(saved));
        return rideMapper.toResponse(saved);
    }

    @Transactional
    public RideResponse startRide(UUID rideId) {
        Ride ride = requireAssignedDriverRide(rideId);
        // Startable whether or not the driver explicitly marked arrival first,
        // so a driver can go straight from en-route to moving.
        if (ride.getStatus() != RideStatus.ACCEPTED
                && ride.getStatus() != RideStatus.ARRIVED) {
            throw new ConflictException(
                    "Ride is " + ride.getStatus() + " and cannot be started");
        }
        ride.setStatus(RideStatus.STARTED);
        Ride saved = rideRepository.save(ride);

        // Open the fare ledger and per-rider progress at start, so each rider's
        // cash can be settled as they're dropped (not all at the end).
        createPaymentsForRide(saved);
        seedRiderProgress(saved);

        notificationPublisher.publish(
                RideNotificationEvent.RIDE_STARTED, saved, hostAndParticipants(saved));
        return rideMapper.toResponse(saved);
    }

    /** Seed one WAITING progress row per rider (host + co-passengers) the first
     *  time a ride starts. Idempotent. */
    private void seedRiderProgress(Ride ride) {
        if (rideRiderProgressRepository.existsByRideId(ride.getId())) {
            return;
        }
        for (UUID riderId : hostAndParticipants(ride)) {
            RideRiderProgress rp = new RideRiderProgress();
            rp.setRideId(ride.getId());
            rp.setUserId(riderId);
            rp.setStatus(RiderProgressStatus.WAITING);
            rideRiderProgressRepository.save(rp);
        }
    }

    /** The assigned driver marks a rider as picked up (in the vehicle). */
    @Transactional
    public void markRiderPickedUp(UUID rideId, UUID riderId) {
        Ride ride = requireAssignedDriverRide(rideId);
        if (ride.getStatus() != RideStatus.STARTED) {
            throw new ConflictException("Ride is " + ride.getStatus()
                    + " — riders can only be picked up on a started trip");
        }
        RideRiderProgress rp = requireRider(rideId, riderId);
        if (rp.getStatus() == RiderProgressStatus.WAITING) {
            rp.setStatus(RiderProgressStatus.PICKED);
            rp.setPickedAt(Instant.now());
            rideRiderProgressRepository.save(rp);
        }
    }

    /** The assigned driver drops a rider at their stop — this settles that
     *  rider's cash fare (marks their payment COLLECTED). */
    @Transactional
    public void markRiderDroppedOff(UUID rideId, UUID riderId) {
        Ride ride = requireAssignedDriverRide(rideId);
        if (ride.getStatus() != RideStatus.STARTED) {
            throw new ConflictException("Ride is " + ride.getStatus()
                    + " — riders can only be dropped on a started trip");
        }
        RideRiderProgress rp = requireRider(rideId, riderId);
        if (rp.getStatus() != RiderProgressStatus.DROPPED) {
            rp.setStatus(RiderProgressStatus.DROPPED);
            rp.setDroppedAt(Instant.now());
            rideRiderProgressRepository.save(rp);
        }
        // Settle the rider's cash on drop-off.
        ridePaymentRepository.findByRideIdAndUserId(rideId, riderId).ifPresent(p -> {
            if (p.getStatus() != PaymentStatus.COLLECTED) {
                p.setStatus(PaymentStatus.COLLECTED);
                p.setCollectedAt(Instant.now());
                ridePaymentRepository.save(p);
            }
        });
    }

    private RideRiderProgress requireRider(UUID rideId, UUID riderId) {
        return rideRiderProgressRepository.findByRideIdAndUserId(rideId, riderId)
                .orElseThrow(() -> new NotFoundException("Rider not on this ride"));
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

        // Ensure the fare ledger exists (normally opened at START), then settle
        // any fares not already collected per-drop — completing the trip means
        // all cash is in hand, so driver earnings reflect the full ride.
        createPaymentsForRide(saved);
        Instant now = Instant.now();
        for (RidePayment p : ridePaymentRepository.findByRideId(saved.getId())) {
            if (p.getStatus() != PaymentStatus.COLLECTED) {
                p.setStatus(PaymentStatus.COLLECTED);
                p.setCollectedAt(now);
                ridePaymentRepository.save(p);
            }
        }

        // Host + co-passengers + the driver.
        List<UUID> recipients = hostAndParticipants(saved);
        if (saved.getDriverId() != null) {
            recipients.add(saved.getDriverId());
        }
        notificationPublisher.publish(RideNotificationEvent.RIDE_COMPLETED, saved, recipients);
        return rideMapper.toResponse(saved);
    }

    /** Create one PENDING cash payment per rider (host + co-passengers) for
     *  their equal share of the fare. Idempotent — skips if already opened. */
    private void createPaymentsForRide(Ride ride) {
        if (ridePaymentRepository.existsByRideId(ride.getId())) {
            return;
        }
        // Each rider owes their WEIGHTED share (leg × seats, host solo-capped);
        // the shares sum exactly to the gross, so the driver collects in full.
        Map<UUID, Double> shares = priceService.computeShares(ride);
        String currency = pricingProperties.getCurrency();
        for (UUID riderId : hostAndParticipants(ride)) {
            RidePayment p = new RidePayment();
            p.setRideId(ride.getId());
            p.setUserId(riderId);
            p.setAmount(shares.getOrDefault(riderId, 0.0));
            p.setCurrency(currency);
            p.setMethod("CASH");
            p.setStatus(PaymentStatus.PENDING);
            ridePaymentRepository.save(p);
        }
    }

    /** A ride's cash payments (host + co-passengers). Any member of the ride,
     *  the driver included, may view it — the driver to collect, riders to see
     *  their own paid/unpaid state. */
    @Transactional(readOnly = true)
    public List<PaymentResponse> getRidePayments(UUID rideId) {
        UserContext ctx = getCurrentUserContext();
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new NotFoundException("Ride not found"));
        if (!isMemberOrDriver(ride, ctx.userId())) {
            throw new ForbiddenException("You are not on this ride");
        }
        List<RidePayment> payments = ridePaymentRepository.findByRideId(rideId);
        Map<UUID, PassengerSummary> people = fetchPassengerSummaries(
                payments.stream().map(RidePayment::getUserId).toList());
        UUID hostId = ride.getCreatedByUserId();
        return payments.stream()
                .map(p -> toPaymentResponse(p, people, hostId))
                .toList();
    }

    /** The assigned driver confirms they collected a rider's cash fare. */
    @Transactional
    public PaymentResponse collectPayment(UUID rideId, UUID riderId) {
        Ride ride = requireAssignedDriverRide(rideId);
        RidePayment payment = ridePaymentRepository
                .findByRideIdAndUserId(rideId, riderId)
                .orElseThrow(() -> new NotFoundException("Payment not found"));
        if (payment.getStatus() != PaymentStatus.COLLECTED) {
            payment.setStatus(PaymentStatus.COLLECTED);
            payment.setCollectedAt(Instant.now());
            ridePaymentRepository.save(payment);
        }
        Map<UUID, PassengerSummary> people = fetchPassengerSummaries(List.of(riderId));
        return toPaymentResponse(payment, people, ride.getCreatedByUserId());
    }

    private boolean isMemberOrDriver(Ride ride, UUID userId) {
        if (ride.getCreatedByUserId().equals(userId)) {
            return true;
        }
        if (ride.getDriverId() != null && ride.getDriverId().equals(userId)) {
            return true;
        }
        return rideParticipantsRepository.findUserIdsByRideId(ride.getId())
                .contains(userId);
    }

    private PaymentResponse toPaymentResponse(RidePayment p,
            Map<UUID, PassengerSummary> people, UUID hostId) {
        return new PaymentResponse(
                p.getUserId().toString(),
                resolveName(people, p.getUserId()),
                p.getAmount(),
                p.getCurrency(),
                p.getMethod(),
                p.getStatus().name(),
                p.getUserId().equals(hostId));
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

    private double dropDistanceKm(Ride r, double dropLat, double dropLng) {
        return priceService.distanceKm(dropLat, dropLng,
                r.getDestination().getLatitude(), r.getDestination().getLongitude());
    }

    private double pickupDistanceKm(Ride r, double lat, double lng) {
        if (r.getPickup() == null) return Double.MAX_VALUE;
        return priceService.distanceKm(lat, lng,
                r.getPickup().getLatitude(), r.getPickup().getLongitude());
    }

    private AvailableRideResponse toAvailableRideResponse(Ride r, Double lat, Double lng,
                                                          Map<UUID, PassengerSummary> hosts) {
        UUID hostId = r.getCreatedByUserId();
        long trips = rideRepository.countCompletedTripsForUser(hostId);

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
                .tripDistanceKm(r.getRouteDistanceKm())
                .tripDurationMin(r.getRouteDurationMin())
                .departureTime(r.getDepartureTime())
                .fareForRider(fareForRider)
                .pickup(r.getPickup() != null ? r.getPickup().getAddress() : null)
                .drop(r.getDestination() != null ? r.getDestination().getAddress() : null)
                .pickupLat(pickupLat)
                .pickupLng(pickupLng)
                .dropLat(dropLat)
                .dropLng(dropLng)
                .seatsAvailable(r.getAvailableSeats())
                .ridersJoined((int) rideParticipantsRepository.countByRide_Id(r.getId()))
                .build();
    }

    @Transactional(readOnly = true)
    public RideDetailsResponse getRideDetails(UUID rideId) {
        UserContext ctx = getCurrentUserContext();
        UUID viewerId = ctx.userId();

        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new NotFoundException("Ride not found"));

        UUID hostId = ride.getCreatedByUserId();
        int hostTrips = (int) rideRepository.countCompletedTripsForUser(hostId);

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

        // Each accepted co-passenger's own route (their join request), keyed by
        // requester — used for their stops AND for the driver's review card.
        Map<UUID, JoinRequest> acceptedByRequester = joinRequestRepository
                .findByRideIdAndStatus(rideId, JoinRequestStatus.ACCEPTED).stream()
                .collect(Collectors.toMap(
                        JoinRequest::getRequesterId, jr -> jr, (a, b) -> a));

        // Phone numbers are shared with drivers (to call riders and coordinate
        // pickup) — not between riders. A driver reviewing/​driving the ride
        // sees them; passengers never see each other's numbers.
        boolean viewerIsDriver = "DRIVER".equals(ctx.role());

        // Per-rider pickup/drop progress (empty until the trip starts).
        Map<UUID, String> progress = rideRiderProgressRepository.findByRideId(rideId)
                .stream()
                .collect(Collectors.toMap(RideRiderProgress::getUserId,
                        p -> p.getStatus().name(), (a, b) -> a));

        // Weighted fare shares (leg × seats, host capped at solo) so every
        // screen can show each rider's real amount.
        Map<UUID, Double> fareShares = priceService.computeShares(ride);

        RideDetailsResponse.HostDto host = RideDetailsResponse.HostDto.builder()
                .id(hostId.toString())
                .name(resolveName(people, hostId))
                .rating(resolveRating(people, hostId))
                .ratingCount((int) ratingRepository.countByRatedId(hostId))
                .trips(hostTrips)
                .gender(ride.getGender() != null ? ride.getGender().name() : null)
                .phone(viewerIsDriver ? resolvePhone(people, hostId) : null)
                .pickupStatus(progress.get(hostId))
                .fareShare(fareShares.get(hostId))
                .build();

        List<RideDetailsResponse.CoPassengerDto> coPassengers = joinedIds.stream()
                .filter(id -> !id.equals(hostId))
                .filter(id -> !id.equals(viewerId))
                .map(id -> {
                    JoinRequest jr = acceptedByRequester.get(id);
                    return RideDetailsResponse.CoPassengerDto.builder()
                            .id(id.toString())
                            .name(resolveName(people, id))
                            .rating(resolveRating(people, id))
                            .ratingCount((int) ratingRepository.countByRatedId(id))
                            .trips((int) rideRepository.countCompletedTripsForUser(id))
                            .gender(null)
                            .phone(viewerIsDriver ? resolvePhone(people, id) : null)
                            .pickup(jr != null ? jr.getPickup() : null)
                            .drop(jr != null ? jr.getDrop() : null)
                            .pickupStatus(progress.get(id))
                            .fareShare(fareShares.get(id))
                            .build();
                })
                .toList();

        // availableSeats on the ride is authoritative (it already accounts for
        // the host's reserved seats + each co-passenger's chosen seats).
        int seatsAvailable = Math.max(0, ride.getAvailableSeats());

        RideDetailsResponse.FareDto fare = priceService.computeFare(ride, viewerId);

        // Derived from the participant ids we already loaded above.
        boolean youHaveJoined = joinedIds.contains(viewerId);

        // The viewer's OWN booked seats: the host's party for the host, a
        // co-passenger's chosen seats for them, else null (just browsing).
        Integer yourSeats;
        if (viewerId.equals(hostId)) {
            yourSeats = ride.getHostSeats();
        } else {
            yourSeats = rideParticipantsRepository
                    .findByRide_IdAndUserId(rideId, viewerId)
                    .map(RideParticipants::getSeats)
                    .orElse(null);
        }

        // The viewer's own latest request for this ride (if any). Drives two
        // things: the pending-request flag, and their personal route labels
        // (so a co-passenger sees THEIR pickup/drop, not the host's). The
        // host has no join request, so skip the lookup for them.
        JoinRequest myRequest = viewerId.equals(hostId)
                ? null
                : joinRequestRepository
                        .findFirstByRideIdAndRequesterIdOrderByCreatedAtDesc(rideId, viewerId)
                        .orElse(null);

        // A viewer who hasn't joined yet may still have a pending request
        // awaiting the host's decision — surface it so the client can keep
        // showing "Request sent" across navigation instead of re-offering
        // the join button (which would let them request twice).
        boolean youHaveRequested = !youHaveJoined
                && myRequest != null
                && myRequest.getStatus() == JoinRequestStatus.PENDING;

        // Surface the viewer's own requested route unless their request was
        // declined (in which case they're not on this ride).
        String yourPickup = null;
        String yourDrop = null;
        if (myRequest != null && myRequest.getStatus() != JoinRequestStatus.DECLINED) {
            yourPickup = myRequest.getPickup();
            yourDrop = myRequest.getDrop();
        }

        Double pickupLat = ride.getPickup() != null ? ride.getPickup().getLatitude() : null;
        Double pickupLng = ride.getPickup() != null ? ride.getPickup().getLongitude() : null;
        Double dropLat = ride.getDestination() != null ? ride.getDestination().getLatitude() : null;
        Double dropLng = ride.getDestination() != null ? ride.getDestination().getLongitude() : null;

        // Every stop on the shared route, for the map polyline: the host's
        // pickup/drop, then each ACCEPTED co-passenger's pickup/drop (carried
        // on their join request). Unordered — the client orders by shortest
        // path with pickup-before-drop per owner. ownerId pairs the two.
        List<RideDetailsResponse.StopDto> stops = new ArrayList<>();
        String hostLabel = resolveName(people, hostId);
        if (pickupLat != null && pickupLng != null) {
            stops.add(RideDetailsResponse.StopDto.builder()
                    .ownerId(hostId.toString()).label(hostLabel)
                    .kind("PICKUP").lat(pickupLat).lng(pickupLng).build());
        }
        if (dropLat != null && dropLng != null) {
            stops.add(RideDetailsResponse.StopDto.builder()
                    .ownerId(hostId.toString()).label(hostLabel)
                    .kind("DROP").lat(dropLat).lng(dropLng).build());
        }
        // Only CURRENT participants get stops. A rider who left has their
        // participant row removed but their JoinRequest stays ACCEPTED (see
        // leaveRide), so keying off accepted requests alone would keep drawing
        // stops for people no longer on the ride. joinedIds is the live
        // participant set; we use the accepted request (built above) for coords.
        for (UUID riderId : joinedIds) {
            JoinRequest jr = acceptedByRequester.get(riderId);
            if (jr == null) continue;
            String riderLabel = resolveName(people, riderId);
            if (jr.getPickupLat() != null && jr.getPickupLng() != null) {
                stops.add(RideDetailsResponse.StopDto.builder()
                        .ownerId(riderId.toString()).label(riderLabel)
                        .kind("PICKUP").lat(jr.getPickupLat()).lng(jr.getPickupLng()).build());
            }
            if (jr.getDropLat() != null && jr.getDropLng() != null) {
                stops.add(RideDetailsResponse.StopDto.builder()
                        .ownerId(riderId.toString()).label(riderLabel)
                        .kind("DROP").lat(jr.getDropLat()).lng(jr.getDropLng()).build());
            }
        }

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
                .departureTime(ride.getDepartureTime())
                .yourSeats(yourSeats)
                .tripDistanceKm(ride.getRouteDistanceKm())
                .tripDurationMin(ride.getRouteDurationMin())
                .host(host)
                .seatsTotal(ride.getTotalSeats())
                .seatsAvailable(seatsAvailable)
                .coPassengers(coPassengers)
                .stops(stops)
                .fare(fare)
                .youHaveJoined(youHaveJoined)
                .youHaveRequested(youHaveRequested)
                .publishedToDrivers(ride.isPublishedToDrivers())
                .yourPickup(yourPickup)
                .yourDrop(yourDrop)
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

    private static String resolvePhone(Map<UUID, PassengerSummary> people, UUID userId) {
        PassengerSummary s = people.get(userId);
        return (s != null && s.phoneNo() != null && !s.phoneNo().isBlank())
                ? s.phoneNo() : null;
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

        // Whether this rider's cash fare was collected, per ride, for the
        // "Paid" badge on completed rides.
        Map<UUID, Boolean> paidByRide = ridePaymentRepository
                .findByUserIdAndRideIdIn(ctx.userId(),
                        rides.stream().map(Ride::getId).toList())
                .stream()
                .collect(Collectors.toMap(RidePayment::getRideId,
                        p -> p.getStatus() == PaymentStatus.COLLECTED, (a, b) -> a || b));

        return rides.stream()
                .map(r -> toHistoryItem(r, drivers, ratingsGiven, paidByRide))
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
                                                       Map<UUID, Integer> ratingsGiven,
                                                       Map<UUID, Boolean> paidByRide) {
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
                .paid(paidByRide.get(r.getId()))
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
            // Real money in hand: the cash the driver actually collected on
            // this ride (settled per-drop), not the expected fare.
            double fare = ridePaymentRepository.findByRideId(r.getId()).stream()
                    .filter(p -> p.getStatus() == PaymentStatus.COLLECTED)
                    .mapToDouble(RidePayment::getAmount)
                    .sum();
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
                // Passenger trips = completed rides they hosted OR joined.
                : rideRepository.countCompletedTripsForUser(ctx.userId());
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