package com.saferide.ride_service.model.dtos;

import java.util.UUID;

public record RideResponse(
        UUID rideId,
        UUID passengerId,
        UUID driverId,
        RideStatus status,
        Double price,
        Double pickupLatitude,
        Double pickupLongitude,
        Double dropLatitude,
        Double dropLongitude
) {
}
