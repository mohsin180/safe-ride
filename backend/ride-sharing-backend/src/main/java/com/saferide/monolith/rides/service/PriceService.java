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
 * All fare maths, in two sentences:
 *
 * <ol>
 *   <li><b>The trip costs</b> {@code baseFare + perKm × km} (times the PREMIUM
 *       multiplier for premium rides, never below {@code minFare}). That is the
 *       whole amount, and it is what the driver collects.</li>
 *   <li><b>Riders split it by distance travelled:</b> each pays the trip fare
 *       times their own km × seats, over everyone's km × seats. Ride 4 km of a
 *       16 km trip and you pay a quarter of what the person going the whole way
 *       pays.</li>
 * </ol>
 *
 * <p>The shares always add up to exactly the trip fare, so the driver is paid
 * in full and no rider is charged for anyone else's distance.
 *
 * <p>Time is deliberately not priced. Duration is still shown on the ride
 * cards, but two riders sitting in the same traffic shouldn't owe more than
 * the same two on an empty road, and a rider has no way to predict it when
 * they agree to a fare.
 *
 * <p>Gone, on purpose: the host fare cap, the redistribution of its excess,
 * the per-extra-seat surcharge, and the "assume one more passenger joins"
 * estimate. Each existed to patch a case the weighted model got wrong; a
 * straight distance split has no such cases, and every screen can now show a
 * number the user can reproduce on paper.
 */
@Service
public class PriceService {

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

    // ── What the trip costs ─────────────────────────────────────────────

    /** The fare for a route of {@code km}, before it is split. */
    public double fareForKm(double km, RideType type) {
        double multiplier =
                type == RideType.PREMIUM ? props.getPremiumMultiplier() : 1.0;
        return round2(Math.max(props.getMinFare(),
                (props.getBaseFare() + km * props.getPerKm()) * multiplier));
    }

    /**
     * The trip total — the driver's take, and the sum of every rider's share.
     * 0.0 if the ride has no route to measure.
     */
    public double tripFare(Ride ride) {
        if (ride.getPickup() == null || ride.getDestination() == null) {
            return 0.0;
        }
        return fareForKm(tripKm(ride), ride.getRideType());
    }

    // ── How it splits ───────────────────────────────────────────────────

    /**
     * Each rider's share of the current trip fare, keyed by userId: the host
     * plus every joined co-passenger, weighted by their own km × seats.
     */
    public Map<UUID, Double> computeShares(Ride ride) {
        return split(ride, tripFare(ride), null, 0, 0);
    }

    /**
     * The shares if a candidate rider joined with their own leg of
     * {@code candidateKm} and {@code candidateSeats}, given the trip fare the
     * ride would then have. Contains the current riders plus the candidate
     * under {@code candidateKey}. Powers the join preview.
     */
    public Map<UUID, Double> previewShares(Ride ride, UUID candidateKey,
                                           double candidateKm, int candidateSeats,
                                           double newTripFare) {
        return split(ride, round2(newTripFare), candidateKey,
                candidateKm, candidateSeats);
    }

    /**
     * Distributes {@code total} across the riders by km × seats.
     *
     * <p>Every distance here is measured the same way — {@link #riderKm} for
     * everyone. Mixing road distance for the host with straight-line distance
     * for joiners, as this used to, quietly under-charged every co-passenger.
     */
    private Map<UUID, Double> split(Ride ride, double total, UUID candidateKey,
                                    double candidateKm, int candidateSeats) {
        UUID hostId = ride.getCreatedByUserId();

        Map<UUID, Double> weights = new LinkedHashMap<>();
        weights.put(hostId, Math.max(0.1, hostLegKm(ride)) * Math.max(1, ride.getHostSeats()));

        Map<UUID, JoinRequest> accepted = new HashMap<>();
        for (JoinRequest jr : joinRequestRepository
                .findByRideIdAndStatus(ride.getId(), JoinRequestStatus.ACCEPTED)) {
            accepted.putIfAbsent(jr.getRequesterId(), jr);
        }
        for (RideParticipants p : rideParticipantsRepository.findByRide_Id(ride.getId())) {
            if (p.getUserId().equals(hostId)) continue;
            int seats = p.getSeats() > 0 ? p.getSeats() : 1;
            weights.put(p.getUserId(), riderKm(ride, accepted.get(p.getUserId())) * seats);
        }
        if (candidateKey != null) {
            weights.put(candidateKey,
                    Math.max(0.1, candidateKm) * Math.max(1, candidateSeats));
        }

        double totalWeight = weights.values().stream()
                .mapToDouble(Double::doubleValue).sum();

        Map<UUID, Double> shares = new LinkedHashMap<>();
        double running = 0;
        UUID last = null;
        for (Map.Entry<UUID, Double> e : weights.entrySet()) {
            double share = round2(total * e.getValue() / totalWeight);
            shares.put(e.getKey(), share);
            running += share;
            last = e.getKey();
        }
        // Rounding leaves a paisa or two unaccounted for; put it on the last
        // rider so the shares sum to the trip fare exactly and the driver is
        // never short-changed.
        if (last != null) {
            shares.merge(last, round2(total - running), Double::sum);
            shares.put(last, round2(shares.get(last)));
        }
        return shares;
    }

    /** A joined rider's own distance — their pickup to their drop. */
    private double riderKm(Ride ride, JoinRequest jr) {
        if (jr != null && jr.getPickupLat() != null && jr.getPickupLng() != null
                && jr.getDropLat() != null && jr.getDropLng() != null) {
            return Math.max(0.1, distanceKm(jr.getPickupLat(), jr.getPickupLng(),
                    jr.getDropLat(), jr.getDropLng()));
        }
        // No coordinates recorded — treat them as riding the host's leg, which
        // is the trip they signed up for.
        return Math.max(0.1, hostLegKm(ride));
    }

    /**
     * The host's own distance: their pickup to their destination, measured the
     * same way as every co-passenger's leg.
     *
     * <p>Deliberately NOT the stored road distance, even though the ride has
     * one. These numbers are only ever compared with each other to work out
     * shares, and mixing units breaks that comparison: the host's road km were
     * being weighed against joiners' straight-line km, so two people making the
     * identical journey were billed 168 and 134 for it. Straight-line for
     * everyone keeps the ratios honest — and keeps previews instant, since
     * routing every rider's leg would mean an API call each.
     *
     * <p>The trip TOTAL still uses the real road distance. Only the split is
     * proportional.
     */
    private double hostLegKm(Ride ride) {
        if (ride.getPickup() == null || ride.getDestination() == null) {
            return 1.0;
        }
        return distanceKm(
                ride.getPickup().getLatitude(), ride.getPickup().getLongitude(),
                ride.getDestination().getLatitude(), ride.getDestination().getLongitude());
    }

    // ── What each screen shows ──────────────────────────────────────────

    /**
     * What a rider browsing the feed would pay if they joined for the same
     * route the host is driving. Honest and reproducible without knowing where
     * they're actually going; the feed uses the exact preview instead whenever
     * the searcher's own route is known.
     *
     * <p>Replaces an estimate that divided by the rider count <em>plus one</em>
     * — a discount for a passenger who hadn't joined, which is why a host
     * riding alone was quoted half of what they actually owed.
     *
     * @return null if the ride has no route to measure
     */
    public Double estimateRiderFare(Ride ride) {
        if (ride.getPickup() == null || ride.getDestination() == null) {
            return null;
        }
        double km = hostLegKm(ride);
        return previewShares(ride, PREVIEW_CANDIDATE, km, 1,
                fareForKm(tripKm(ride), ride.getRideType()))
                .getOrDefault(PREVIEW_CANDIDATE, 0.0);
    }

    /** Key for a hypothetical rider inside a preview split. */
    private static final UUID PREVIEW_CANDIDATE = new UUID(0L, 0L);

    /**
     * The fare breakdown for the ride-details screen, from the viewer's point
     * of view: {@code perRider} is their own share when they're on the ride,
     * otherwise what they'd pay to join for the whole route. {@code baseFare}
     * is the trip total.
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
        double total = tripFare(ride);
        Map<UUID, Double> shares = computeShares(ride);
        Double perRider = viewerId != null && shares.containsKey(viewerId)
                ? shares.get(viewerId)
                : estimateRiderFare(ride);
        return RideDetailsResponse.FareDto.builder()
                .baseFare(total)               // the whole trip — the driver's take
                .sharedDiscount(0.0)
                .perRider(perRider == null ? 0.0 : perRider)
                .currency(props.getCurrency())
                .build();
    }

    /** Legacy entry point — no viewer context. */
    public RideDetailsResponse.FareDto computeFare(Ride ride) {
        return computeFare(ride, null);
    }

    // ── Distance ────────────────────────────────────────────────────────

    /** Road distance in km: the persisted route distance, else Haversine. */
    private double tripKm(Ride ride) {
        if (ride.getRouteDistanceKm() != null) {
            return ride.getRouteDistanceKm();
        }
        return distanceKm(
                ride.getPickup().getLatitude(), ride.getPickup().getLongitude(),
                ride.getDestination().getLatitude(), ride.getDestination().getLongitude());
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
