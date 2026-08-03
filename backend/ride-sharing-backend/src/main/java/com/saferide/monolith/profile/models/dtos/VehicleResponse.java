package com.saferide.monolith.profile.models.dtos;

public record VehicleResponse(
        String make,
        String model,
        String number,
        String color,
        int seats,
        int year
) {
}
