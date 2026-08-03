package com.saferide.monolith.rides.model.dtos;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Body for reporting a user on a ride. */
public record ReportUserRequest(
        @NotNull(message = "reportedUserId is required")
        UUID reportedUserId,
        String reason
) {
}
