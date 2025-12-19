package com.saferide.profile_service.models.dtos;

public record DriverProfileRequest(
        String fullName,
        String vehicleModel,
        String vehicleNumber,
        String licenseNumber
) {
}
