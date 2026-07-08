package com.safe_ride.rides_service.model.dtos;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Body for reporting a user on a ride. */
public record ReportUserRequest(
        @NotNull(message = "reportedUserId is required")
        UUID reportedUserId,
        String reason
) {
}
