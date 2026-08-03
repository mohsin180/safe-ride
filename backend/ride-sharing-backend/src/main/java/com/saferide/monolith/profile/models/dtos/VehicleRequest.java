package com.saferide.monolith.profile.models.dtos;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
        @Min(value = 1, message = "Car seats must be between 1 and 4")
        @Max(value = 4, message = "Car seats must be between 1 and 4")
        int seats,

        @NotNull(message = "Car year is required")
        int year
) {
}
