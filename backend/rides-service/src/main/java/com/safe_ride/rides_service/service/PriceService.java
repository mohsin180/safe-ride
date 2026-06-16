package com.safe_ride.rides_service.service;

import com.safe_ride.rides_service.config.PricingProperties;
import com.safe_ride.rides_service.model.dtos.RideDetailsResponse;
import com.safe_ride.rides_service.model.entity.Ride;
import com.safe_ride.rides_service.model.entity.RideType;
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

    public PriceService(PricingProperties props) {
        this.props = props;
    }

    /**
     * The per-rider estimate shown in the "available rides" / driver feed as
     * "Your fare". This now returns the EXACT same value as
     * {@code computeFare(...).perRider} (via the shared {@link #perSeatFare}
     * helper), so the feed and the ride-details screen always agree. Returns
     * null if the ride has no pickup/destination to measure.
     */
    public Double estimateRiderFare(Ride ride) {
        if (ride.getPickup() == null || ride.getDestination() == null) {
            return null;
        }
        return perSeatFare(ride);
    }

    /**
     * Full fare breakdown for the ride-details screen. The {@code seatsBooked}
     * argument is kept for the call-site signature but no longer drives the
     * per-rider price — {@code perRider} is now a fixed per-seat price (same as
     * the feed estimate), intentionally independent of how many have booked.
     */
    public RideDetailsResponse.FareDto computeFare(Ride ride, int seatsBooked) {
        if (ride.getPickup() == null || ride.getDestination() == null) {
            return RideDetailsResponse.FareDto.builder()
                    .baseFare(0.0)
                    .sharedDiscount(0.0)
                    .perRider(0.0)
                    .currency(props.getCurrency())
                    .build();
        }
        double gross = gross(ride);
        int totalSeats = Math.max(1, ride.getTotalSeats());
        double discount = totalSeats > 1 ? round2(gross * props.getSharedDiscountRate()) : 0.0;
        return RideDetailsResponse.FareDto.builder()
                .baseFare(round2(gross))
                .sharedDiscount(discount)
                .perRider(perSeatFare(ride))
                .currency(props.getCurrency())
                .build();
    }

    /**
     * The un-split gross trip total — the driver's gross for the trip, used by
     * driver ride-history fares and earnings. Returns 0.0 if the ride has no
     * pickup/destination to measure.
     */
    public double tripFare(Ride ride) {
        if (ride.getPickup() == null || ride.getDestination() == null) {
            return 0.0;
        }
        return round2(gross(ride));
    }

    /**
     * The single source of truth for the per-seat price. Used by BOTH the feed
     * estimate ({@link #estimateRiderFare}) and the details breakdown
     * ({@code computeFare(...).perRider}) so the two can never diverge:
     * {@code (gross - sharedDiscount) / totalSeats}, where the shared discount
     * only applies when the ride seats more than one.
     */
    private double perSeatFare(Ride ride) {
        double gross = gross(ride);
        int totalSeats = Math.max(1, ride.getTotalSeats());
        double discount = totalSeats > 1 ? gross * props.getSharedDiscountRate() : 0.0;
        return round2((gross - discount) / totalSeats);
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
