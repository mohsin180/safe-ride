package com.saferide.profile_service.models.dtos;

import java.util.UUID;

/**
 * Lightweight driver projection used by other services (e.g. rides-service
 * via Feign) to show who drove a completed ride. {@code carInfo} is
 * pre-composed from the vehicle (e.g. "Toyota Corolla · White") so callers
 * don't need the full vehicle shape. Intentionally excludes PII like
 * CNIC / phone.
 */
public record DriverSummaryResponse(
        UUID userId,
        String fullName,
        String carInfo,
        Double rating
) {
}
