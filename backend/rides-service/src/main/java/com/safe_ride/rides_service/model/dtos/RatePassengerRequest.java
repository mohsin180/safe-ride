package com.safe_ride.rides_service.model.dtos;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Body for the driver rating one of the ride's passengers. */
public record RatePassengerRequest(
        @NotNull UUID ratedId,
        @Min(1) @Max(5) int stars
) {
}
