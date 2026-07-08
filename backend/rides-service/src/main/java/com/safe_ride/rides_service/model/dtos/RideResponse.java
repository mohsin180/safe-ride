package com.safe_ride.rides_service.model.dtos;

import com.safe_ride.rides_service.model.entity.RideStatus;
import com.safe_ride.rides_service.model.entity.RideType;

import java.time.Instant;
import java.util.UUID;

public record RideResponse(
        UUID id,
        UUID passengerId,
        UUID driverId,
        String pickup,
        String drop,
        Double pickupLat,
        Double pickupLng,
        Double dropLat,
        Double dropLng,
        int seats,
        RideType rideType,
        RideStatus status,
        Instant createdAt,
        /** Scheduled departure; null = on-demand ("leave now"). */
        Instant departureTime
) {
}