package com.saferide.monolith.rides.model.dtos;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** Body for a passenger rating the ride's driver. */
public record RateDriverRequest(
        @Min(1) @Max(5) int stars
) {
}
