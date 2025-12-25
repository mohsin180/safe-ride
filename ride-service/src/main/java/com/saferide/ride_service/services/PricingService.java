package com.saferide.ride_service.services;

import org.springframework.stereotype.Service;

@Service
public class PricingService {

    private static final double BASE_FARE = 100;
    private static final double PER_KM_RATE = 30;

    public double calculatePrice(
            double pickupLat,
            double pickupLng,
            double dropLat,
            double dropLng
    ) {
        double distance = calculateDistance(pickupLat, pickupLng, dropLat, dropLng);
        return BASE_FARE + (distance * PER_KM_RATE);
    }

    // Haversine formula
    private double calculateDistance(
            double lat1, double lon1,
            double lat2, double lon2
    ) {
        double R = 6371;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a =
                Math.sin(dLat / 2) * Math.sin(dLat / 2)
                        + Math.cos(Math.toRadians(lat1))
                        * Math.cos(Math.toRadians(lat2))
                        * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}

