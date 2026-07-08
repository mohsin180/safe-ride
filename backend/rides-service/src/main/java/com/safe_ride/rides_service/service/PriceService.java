package com.safe_ride.rides_service.service;

import com.safe_ride.rides_service.config.PricingProperties;
import com.safe_ride.rides_service.model.dtos.RideDetailsResponse;
import com.safe_ride.rides_service.model.entity.Ride;
import com.safe_ride.rides_service.model.entity.RideType;
import com.safe_ride.rides_service.repo.RideParticipantsRepository;
import org.springframework.stereotype.Service;

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

    public PriceService(PricingProperties props,
                        RideParticipantsRepository rideParticipantsRepository) {
        this.props = props;
        this.rideParticipantsRepository = rideParticipantsRepository;
    }

    /**
     * The per-rider fare shown in the "available rides" / driver feed as
     * "Your fare". Same value as {@code computeFare(...).perRider} (via the
     * shared {@link #perRiderFare} helper) so the feed and the ride-details
     * screen always agree. Returns null if the ride has no pickup/destination
     * to measure.
     */
    public Double estimateRiderFare(Ride ride) {
        if (ride.getPickup() == null || ride.getDestination() == null) {
            return null;
        }
        return perRiderFare(ride);
    }

    /**
     * Full fare breakdown for the ride-details screen. The gross trip cost is
     * split evenly among the ACTUAL riders (host + joined co-passengers). No
     * discount is applied — the driver always collects the full gross; riders
     * simply share it (so more riders = cheaper each, driver take unchanged).
     */
    public RideDetailsResponse.FareDto computeFare(Ride ride) {
        if (ride.getPickup() == null || ride.getDestination() == null) {
            return RideDetailsResponse.FareDto.builder()
                    .baseFare(0.0)
                    .sharedDiscount(0.0)
                    .perRider(0.0)
                    .currency(props.getCurrency())
                    .build();
        }
        double gross = gross(ride);
        return RideDetailsResponse.FareDto.builder()
                .baseFare(round2(gross))       // full trip cost
                .sharedDiscount(0.0)           // no discount, solo or shared
                .perRider(perRiderFare(ride))  // each rider's equal share
                .currency(props.getCurrency())
                .build();
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
        return round2(gross(ride));
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
