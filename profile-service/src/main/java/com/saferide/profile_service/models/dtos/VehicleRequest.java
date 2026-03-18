package com.saferide.profile_service.models.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record VehicleRequest(
        @NotBlank(message = "Car make is required")
        String make,
        @NotBlank(message = "Car model is required")
        String model,

        @NotBlank(message = "Car number is required")
        String number,

        @NotBlank(message = "Car color is required")
        String color,
        @NotNull(message = "Car Seats are required")
        int seats
) {
}
