package com.saferide.monolith.rides.service;

import com.saferide.monolith.rides.config.PricingProperties;
import com.saferide.monolith.rides.model.dtos.RideDetailsResponse;
import com.saferide.monolith.rides.model.entity.JoinRequest;
import com.saferide.monolith.rides.model.entity.JoinRequestStatus;
import com.saferide.monolith.rides.model.entity.Ride;
import com.saferide.monolith.rides.model.entity.RideParticipants;
import com.saferide.monolith.rides.model.entity.RideType;
import com.saferide.monolith.rides.repo.JoinRequestRepository;
import com.saferide.monolith.rides.repo.RideParticipantsRepository;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Owns all fare/pricing math for rides. Keeping it in one place means the
 * pricing rules live in a single spot instead of being scattered across
 * RideService. All the constants (base fare, per-km/per-min rates, shared
 * discount, premium multiplier, min fare, currency) are externalized into
 * {@link PricingProperties} so they're config-driven.
 *
 * <p>The fare model is server-authoritative, road-distance + time based,
 * rideType-aware: it prefers the road distance/duration persisted on the ride
 * (from the routing API at creation time) and only falls back to a Haversine
 * straight-line estimate for legacy rides that have neither stored.
 */
@Service
public class PriceService {

    /** Average city speed used to estimate trip duration from distance when no
     *  routed duration is stored (km / 30 km/h * 60 = minutes). */
    private static final double FALLBACK_SPEED_KMPH = 30.0;

    private final PricingProperties props;
    private final RideParticipantsRepository rideParticipantsRepository;
    private final JoinRequestRepository joinRequestRepository;

    public PriceService(PricingProperties props,
                        RideParticipantsRepository rideParticipantsRepository,
                        JoinRequestRepository joinRequestRepository) {
        this.props = props;
        this.rideParticipantsRepository = rideParticipantsRepository;
        this.joinRequestRepository = joinRequestRepository;
    }

    // ─── Dynamic weighted pricing ───────────────────────────────────────
    //
    // Each rider's share of the trip gross is weighted by (own-leg km ×
    // seats): whoever travels farther / books more seats pays more. The HOST
    // is capped at their SOLO fare (the fare of their original leg alone) —
    // co-passengers joining can only ever LOWER the host's cost, never raise
    // it; any detour cost above the cap shifts to the detour-causing riders.
    // The shares always sum exactly to the gross, so the driver is paid in
    // full for the route actually driven.

    /** Gross fare for raw metrics — same formula as the ride gross. */
    public double grossFromMetrics(double km, double min, RideType type) {
        double multiplier =
                type == RideType.PREMIUM ? props.getPremiumMultiplier() : 1.0;
        double raw = (props.getBaseFare() + km * props.getPerKm()
                + min * props.getPerMin()) * multiplier;
        return Math.max(props.getMinFare(), raw);
    }

    /** The host's solo fare — what their original leg costs alone. */
    public double soloHostGross(Ride ride) {
        double km = hostLegKm(ride);
        double min = ride.getHostLegMin() != null
                ? ride.getHostLegMin()
                : (km / FALLBACK_SPEED_KMPH) * 60.0;
        return round2(grossFromMetrics(km, min, ride.getRideType()));
    }

    private double hostLegKm(Ride ride) {
        if (ride.getHostLegKm() != null) {
            return ride.getHostLegKm();
        }
        if (ride.getPickup() == null || ride.getDestination() == null) {
            return 1.0;
        }
        return distanceKm(
                ride.getPickup().getLatitude(), ride.getPickup().getLongitude(),
                ride.getDestination().getLatitude(),
                ride.getDestination().getLongitude());
    }

    /**
     * Weighted per-rider shares of the CURRENT gross (host + every joined
     * co-passenger), keyed by userId. Weight = own-leg km × seats; host
     * capped at their solo fare with the excess redistributed over the
     * co-passengers by weight. Shares sum exactly to the (rounded) gross.
     */
    public Map<UUID, Double> computeShares(Ride ride) {
        return sharesFor(ride, round2(gross(ride)), null, 0, 0);
    }

    /**
     * Would-be shares if a candidate rider (own leg {@code candidateLegKm},
     * {@code candidateSeats}) joined, given the simulated {@code newGross}.
     * Map contains the current riders plus the candidate under
     * {@code candidateKey} — powers both fare previews.
     */
    public Map<UUID, Double> previewShares(Ride ride, UUID candidateKey,
                                           double candidateLegKm, int candidateSeats,
                                           double newGross) {
        return sharesFor(ride, round2(newGross), candidateKey,
                candidateLegKm, candidateSeats);
    }

    private Map<UUID, Double> sharesFor(Ride ride, double gross, UUID candidateKey,
                                        double candidateLegKm, int candidateSeats) {
        UUID hostId = ride.getCreatedByUserId();

        // Weights: host first, then each participant by their own leg.
        Map<UUID, Double> weights = new LinkedHashMap<>();
        double hostLeg = Math.max(0.1, hostLegKm(ride));
        int hostSeats = Math.max(1, ride.getHostSeats());
        weights.put(hostId, hostLeg * hostSeats);

        // Seats each rider occupies, kept alongside the weights so the
        // extra-seat surcharge can be applied per rider at the end.
        Map<UUID, Integer> seatsByRider = new LinkedHashMap<>();
        seatsByRider.put(hostId, hostSeats);

        Map<UUID, JoinRequest> accepted = new HashMap<>();
        for (JoinRequest jr : joinRequestRepository
                .findByRideIdAndStatus(ride.getId(), JoinRequestStatus.ACCEPTED)) {
            accepted.putIfAbsent(jr.getRequesterId(), jr);
        }
        for (RideParticipants p : rideParticipantsRepository.findByRide_Id(ride.getId())) {
            if (p.getUserId().equals(hostId)) continue;
            JoinRequest jr = accepted.get(p.getUserId());
            double leg = hostLeg; // neutral fallback when no coords
            if (jr != null && jr.getPickupLat() != null && jr.getPickupLng() != null
                    && jr.getDropLat() != null && jr.getDropLng() != null) {
                leg = Math.max(0.1, distanceKm(jr.getPickupLat(), jr.getPickupLng(),
                        jr.getDropLat(), jr.getDropLng()));
            }
            int seats = p.getSeats() > 0 ? p.getSeats() : 1;
            weights.put(p.getUserId(), leg * seats);
            seatsByRider.put(p.getUserId(), seats);
        }
        if (candidateKey != null) {
            int seats = Math.max(1, candidateSeats);
            weights.put(candidateKey, Math.max(0.1, candidateLegKm) * seats);
            seatsByRider.put(candidateKey, seats);
        }

        double totalW = weights.values().stream().mapToDouble(Double::doubleValue).sum();
        Map<UUID, Double> shares = new LinkedHashMap<>();
        for (Map.Entry<UUID, Double> e : weights.entrySet()) {
            shares.put(e.getKey(), gross * e.getValue() / totalW);
        }

        // Host cap: never above their solo fare; excess flows to the
        // co-passengers (the detour-causers), proportionally by weight.
        if (shares.size() > 1) {
            double cap = soloHostGross(ride);
            double hostShare = shares.get(hostId);
            if (hostShare > cap) {
                double excess = hostShare - cap;
                shares.put(hostId, cap);
                double othersW = totalW - weights.get(hostId);
                for (Map.Entry<UUID, Double> e : weights.entrySet()) {
                    if (e.getKey().equals(hostId)) continue;
                    shares.merge(e.getKey(), excess * e.getValue() / othersW, Double::sum);
                }
            }
        }

        // Round to 2dp and pin the drift on the largest share so the sum is
        // exactly the gross (driver collects the full fare, no paisa gaps).
        UUID largest = hostId;
        double largestV = -1;
        double sum = 0;
        for (Map.Entry<UUID, Double> e : shares.entrySet()) {
            double r = round2(e.getValue());
            shares.put(e.getKey(), r);
            sum += r;
            if (r > largestV) { largestV = r; largest = e.getKey(); }
        }
        shares.merge(largest, round2(gross - sum), Double::sum);
        shares.put(largest, round2(shares.get(largest)));

        // Extra-seat surcharge, applied last so it rides on top of a split
        // that already sums to the gross. Driving the trip costs the same
        // whether one rider takes one seat or three, but the held seats stop
        // anyone else joining — so the total ends up ABOVE the gross, and
        // tripFare() sums the shares rather than returning the gross.
        applySeatSurcharge(shares, seatsByRider);
        return shares;
    }

    private void applySeatSurcharge(Map<UUID, Double> shares, Map<UUID, Integer> seatsByRider) {
        double rate = props.getSeatSurchargeRate();
        if (rate <= 0) {
            return;
        }
        for (Map.Entry<UUID, Double> e : shares.entrySet()) {
            int extraSeats = Math.max(0, seatsByRider.getOrDefault(e.getKey(), 1) - 1);
            if (extraSeats > 0) {
                shares.put(e.getKey(), round2(e.getValue() * (1 + rate * extraSeats)));
            }
        }
    }

    /**
     * ROUGH entry estimate for a browsing rider with an unknown route: the
     * gross split over one-more-head. The feed uses the accurate weighted
     * preview when the searcher's route is known; this is only the fallback.
     * Returns null if the ride has no pickup/destination to measure.
     */
    public Double estimateRiderFare(Ride ride) {
        if (ride.getPickup() == null || ride.getDestination() == null) {
            return null;
        }
        return round2(gross(ride) / (riderCount(ride) + 1));
    }

    /**
     * Fare breakdown for the ride-details screen, personalised to the viewer:
     * {@code perRider} is the VIEWER's own weighted share when they're on the
     * ride, else the average share. {@code baseFare} stays the full gross.
     */
    public RideDetailsResponse.FareDto computeFare(Ride ride, UUID viewerId) {
        if (ride.getPickup() == null || ride.getDestination() == null) {
            return RideDetailsResponse.FareDto.builder()
                    .baseFare(0.0)
                    .sharedDiscount(0.0)
                    .perRider(0.0)
                    .currency(props.getCurrency())
                    .build();
        }
        double gross = round2(gross(ride));
        Map<UUID, Double> shares = computeShares(ride);
        double perRider = viewerId != null && shares.containsKey(viewerId)
                ? shares.get(viewerId)
                : round2(gross / Math.max(1, shares.size()));
        return RideDetailsResponse.FareDto.builder()
                .baseFare(gross)          // full trip cost (driver's take)
                .sharedDiscount(0.0)
                .perRider(perRider)       // the viewer's own weighted share
                .currency(props.getCurrency())
                .build();
    }

    /** Legacy entry point — average share (no viewer context). */
    public RideDetailsResponse.FareDto computeFare(Ride ride) {
        return computeFare(ride, null);
    }

    /**
     * The trip total the driver collects — the full gross (all riders' shares
     * combined). Used by driver ride-history fares and earnings. 0.0 if the
     * ride has no route to measure.
     */
    public double tripFare(Ride ride) {
        if (ride.getPickup() == null || ride.getDestination() == null) {
            return 0.0;
        }
        // Sum of the shares, not the bare gross: extra-seat surcharges are
        // real money the riders hand over, so the driver's earnings have to
        // include them.
        return round2(computeShares(ride).values().stream()
                .mapToDouble(Double::doubleValue).sum());
    }

    /** Number of riders splitting the fare: the host plus every joined
     *  co-passenger. Always at least 1. */
    private int riderCount(Ride ride) {
        int coPassengers =
                rideParticipantsRepository.findUserIdsByRideId(ride.getId()).size();
        return 1 + coPassengers;
    }

    /**
     * Each rider's share: the gross split evenly across the actual riders (no
     * discount). The single source of truth for both the feed estimate and the
     * details breakdown so they can never diverge.
     */
    private double perRiderFare(Ride ride) {
        return round2(gross(ride) / Math.max(1, riderCount(ride)));
    }

    /**
     * Gross trip fare:
     * {@code max(minFare, (baseFare + km*perKm + min*perMin) * premiumMultiplier)}
     * where the multiplier is {@code premiumMultiplier} for PREMIUM rides else 1.0.
     * Distance/duration come from the ride's persisted route values, falling
     * back to Haversine + a 30 km/h duration estimate for legacy rides.
     */
    private double gross(Ride ride) {
        double km = tripKm(ride);
        double min = tripMinutes(ride);
        double multiplier = ride.getRideType() == RideType.PREMIUM
                ? props.getPremiumMultiplier()
                : 1.0;
        double raw = (props.getBaseFare() + km * props.getPerKm() + min * props.getPerMin())
                * multiplier;
        return Math.max(props.getMinFare(), raw);
    }

    /** Road distance in km: the persisted route distance, else Haversine. */
    private double tripKm(Ride ride) {
        if (ride.getRouteDistanceKm() != null) {
            return ride.getRouteDistanceKm();
        }
        return distanceKm(
                ride.getPickup().getLatitude(),
                ride.getPickup().getLongitude(),
                ride.getDestination().getLatitude(),
                ride.getDestination().getLongitude());
    }

    /** Trip duration in minutes: the persisted route duration, else a
     *  distance/30 km/h estimate (legacy rides). */
    private double tripMinutes(Ride ride) {
        if (ride.getRouteDurationMin() != null) {
            return ride.getRouteDurationMin();
        }
        return (tripKm(ride) / FALLBACK_SPEED_KMPH) * 60.0;
    }

    /**
     * Great-circle distance in km between two coordinates (Haversine). Public so
     * callers can also use it for non-pricing distances (e.g. rider -> pickup).
     */
    public double distanceKm(double lat1, double lng1, double lat2, double lng2) {
        final double earthRadiusKm = 6371;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * earthRadiusKm * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
