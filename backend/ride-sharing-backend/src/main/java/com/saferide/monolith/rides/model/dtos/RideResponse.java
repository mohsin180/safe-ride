package com.saferide.monolith.rides.model.dtos;

import com.saferide.monolith.rides.model.entity.RideStatus;
import com.saferide.monolith.rides.model.entity.RideType;

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
        Instant createdAt
) {
}