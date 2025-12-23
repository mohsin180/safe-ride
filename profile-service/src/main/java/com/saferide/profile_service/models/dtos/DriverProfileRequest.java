package com.saferide.profile_service.models.dtos;

import jakarta.validation.constraints.NotBlank;

public record DriverProfileRequest(
        @NotBlank(message = "FullName is required")
        String fullName,
        @NotBlank(message = "VehicleModel is required")
        String vehicleModel,
        @NotBlank(message = "VehicleNumber is required")
        String vehicleNumber,
        @NotBlank(message = "licenseNumber is required")
        String licenseNumber
) {
}
