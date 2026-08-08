package com.saferide.monolith.rides.model.dtos;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Body for requesting to join a ride. Carries the requester's OWN route
 * (from their search) so the host can see where they want to go before
 * accepting. All fields optional — omitted values fall back to the ride's
 * own pickup/destination.
 */
public record JoinRequestBody(
        String pickup,
        @DecimalMin(value = "-90.0", message = "must be between -90 and 90")
        @DecimalMax(value = "90.0", message = "must be between -90 and 90")
        Double pickupLat,
        @DecimalMin(value = "-180.0", message = "must be between -180 and 180")
        @DecimalMax(value = "180.0", message = "must be between -180 and 180")
        Double pickupLng,
        String drop,
        @DecimalMin(value = "-90.0", message = "must be between -90 and 90")
        @DecimalMax(value = "90.0", message = "must be between -90 and 90")
        Double dropLat,
        @DecimalMin(value = "-180.0", message = "must be between -180 and 180")
        @DecimalMax(value = "180.0", message = "must be between -180 and 180")
        Double dropLng,
        /** Seats the co-passenger wants (1..seats-available). Null ⇒ 1. */
        @Min(value = 1, message = "must be between 1 and 4")
        @Max(value = 4, message = "must be between 1 and 4")
        Integer seats
) {
}
