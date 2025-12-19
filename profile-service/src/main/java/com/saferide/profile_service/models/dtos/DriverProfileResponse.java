package com.saferide.profile_service.models.dtos;

import java.util.UUID;

public record DriverProfileResponse(
        UUID id,
        String fullName,
        String vehicleModel,
        String vehicleNumber,
        Double driverRating
) {
}
