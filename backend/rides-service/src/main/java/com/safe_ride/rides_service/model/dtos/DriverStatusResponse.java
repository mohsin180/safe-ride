package com.safe_ride.rides_service.model.dtos;

import java.time.Instant;

/** Current online/offline availability for the authenticated driver. */
public record DriverStatusResponse(
        boolean online,
        Instant lastSeenAt
) {
}
