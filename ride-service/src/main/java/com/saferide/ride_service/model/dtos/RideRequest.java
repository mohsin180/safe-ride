package com.saferide.ride_service.model.dtos;

import jakarta.validation.constraints.NotNull;

public record RideRequest(
        @NotNull(message = "pickup Latitude is required")
        Double pickupLat,
        @NotNull(message = "pickup Longitude is required")
        Double pickupLon,
        @NotNull(message = "Drop Latitude is required")
        Double dropOffLat,
        @NotNull(message = "Drop Longitude is required")
        Double dropOffLon
) {
}
