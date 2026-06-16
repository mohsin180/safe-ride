package com.safe_ride.rides_service.model.dtos;

import com.safe_ride.rides_service.model.entity.RideType;
import jakarta.validation.constraints.*;

public record CreateRideRequest(
        @NotBlank(message = "is required")
        String pickup,

        @NotBlank(message = "is required")
        String drop,

        @NotNull(message = "is required")
        @DecimalMin(value = "-90.0", message = "must be between -90 and 90")
        @DecimalMax(value = "90.0", message = "must be between -90 and 90")
        Double pickupLat,

        @NotNull(message = "is required")
        @DecimalMin(value = "-180.0", message = "must be between -180 and 180")
        @DecimalMax(value = "180.0", message = "must be between -180 and 180")
        Double pickupLng,

        @NotNull(message = "is required")
        @DecimalMin(value = "-90.0", message = "must be between -90 and 90")
        @DecimalMax(value = "90.0", message = "must be between -90 and 90")
        Double dropLat,

        @NotNull(message = "is required")
        @DecimalMin(value = "-180.0", message = "must be between -180 and 180")
        @DecimalMax(value = "180.0", message = "must be between -180 and 180")
        Double dropLng,

        @Min(value = 1, message = "must be between 1 and 4")
        @Max(value = 4, message = "must be between 1 and 4")
        int seats,

        @NotNull(message = "is required (ECONOMY or PREMIUM)")
        RideType rideType
) {
}