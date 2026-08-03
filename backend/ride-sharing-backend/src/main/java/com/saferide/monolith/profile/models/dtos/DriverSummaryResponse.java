package com.saferide.monolith.profile.models.dtos;

import java.util.UUID;

/**
 * Lightweight driver projection used by other services (e.g. rides-service
 * via Feign) to show who drove a completed ride, or to surface the assigned
 * driver on a passenger's active trip. {@code carInfo} is pre-composed from
 * the vehicle (e.g. "Toyota Corolla · White") so callers don't need the full
 * vehicle shape. {@code phoneNo} is exposed so the rider can call the driver
 * during an in-progress trip. Intentionally excludes other PII like CNIC.
 */
public record DriverSummaryResponse(
        UUID userId,
        String fullName,
        String carInfo,
        Double rating,
        String phoneNo
) {
}
