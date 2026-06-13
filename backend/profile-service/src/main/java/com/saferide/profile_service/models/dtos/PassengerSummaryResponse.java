package com.saferide.profile_service.models.dtos;

import java.util.UUID;

/**
 * Lightweight passenger projection used by other services (e.g.
 * rides-service via Feign) to resolve a userId into a display name +
 * rating. Intentionally excludes PII like CNIC / phone — only what
 * another service needs to render a host / co-passenger card.
 */
public record PassengerSummaryResponse(
        UUID userId,
        String fullName,
        Double rating
) {
}
