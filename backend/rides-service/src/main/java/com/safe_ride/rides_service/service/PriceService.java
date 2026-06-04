package com.safe_ride.rides_service.service;

import com.safe_ride.rides_service.model.dtos.RideDetailsResponse;
import com.safe_ride.rides_service.model.entity.Ride;
import org.springframework.stereotype.Service;

/**
 * Owns all fare/pricing math for rides. Keeping it in one place means the
 * pricing rules (base fare, per-km rate, shared-ride discount, currency) live
 * in a single spot instead of being scattered across RideService.
 */
@Service
public class PriceService {

    private static final double BASE_FARE = 50.0;       // flat pickup charge
    private static final double PER_KM_RATE = 20.0;     // charge per km of trip distance
    private static final double SHARED_DISCOUNT_RATE = 0.20; // 20% off when the ride is shared
    private static final String CURRENCY = "PKR";

    /**
     * Rough per-rider estimate shown in the "available rides" list: the full
     * trip cost split evenly across every seat, with no shared discount yet
     * (we don't know how many will actually book). Returns null if the ride has
     * no pickup/destination to measure.
     */
    public Double estimateRiderFare(Ride ride) {
        if (ride.getPickup() == null || ride.getDestination() == null) {
            return null;
        }
        double total = BASE_FARE + tripKm(ride) * PER_KM_RATE;
        int seats = Math.max(ride.getTotalSeats(), 1);
        return round2(total / seats);
    }

    /**
     * Full fare breakdown for the ride-details screen. Splits the cost across
     * the riders who have actually booked, and applies the shared discount once
     * more than one rider is on board.
     */
    public RideDetailsResponse.FareDto computeFare(Ride ride, int seatsBooked) {
        if (ride.getPickup() == null || ride.getDestination() == null) {
            return RideDetailsResponse.FareDto.builder()
                    .baseFare(0.0)
                    .sharedDiscount(0.0)
                    .perRider(0.0)
                    .currency(CURRENCY)
                    .build();
        }
        double base = round2(BASE_FARE + tripKm(ride) * PER_KM_RATE);
        double discount = round2(seatsBooked > 1 ? base * SHARED_DISCOUNT_RATE : 0.0);
        double perRider = round2((base - discount) / Math.max(1, seatsBooked));
        return RideDetailsResponse.FareDto.builder()
                .baseFare(base)
                .sharedDiscount(discount)
                .perRider(perRider)
                .currency(CURRENCY)
                .build();
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

    private double tripKm(Ride ride) {
        return distanceKm(
                ride.getPickup().getLatitude(),
                ride.getPickup().getLongitude(),
                ride.getDestination().getLatitude(),
                ride.getDestination().getLongitude());
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
