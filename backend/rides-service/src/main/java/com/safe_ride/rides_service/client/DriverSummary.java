package com.safe_ride.rides_service.client;

import java.util.UUID;

/**
 * Mirror of profile-service's {@code DriverSummaryResponse}. Just the
 * fields rides-service needs to show who drove a completed ride on the
 * passenger history screen. {@code carInfo} is pre-composed by
 * profile-service (e.g. "Toyota Corolla · White").
 */
public record DriverSummary(
        UUID userId,
        String fullName,
        String carInfo,
        Double rating
) {
}
