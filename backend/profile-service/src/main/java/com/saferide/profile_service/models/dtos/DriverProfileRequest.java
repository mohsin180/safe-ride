package com.saferide.profile_service.models.dtos;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DriverProfileRequest(
        @NotBlank(message = "FullName is required")
        String fullName,
        @NotBlank(message = "CNIC is required")
        String cnic,
        @NotBlank(message = "PhoneNo is required")
        String phoneNo,
        @Valid
        @NotNull(message = "Vehicle details are required")
        VehicleRequest vehicle
) {
}
