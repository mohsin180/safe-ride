package com.saferide.profile_service.models.dtos;

public record VehicleResponse(
        String make,
        String model,
        String number,
        String color,
        int seats
) {
}
