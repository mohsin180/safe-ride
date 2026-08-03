package com.saferide.monolith.profile.models.dtos;

import java.util.UUID;

/**
 * Lightweight passenger projection used by other services (e.g.
 * rides-service via Feign) to resolve a userId into a display name, rating,
 * and contact number. Excludes heavier PII like CNIC; {@code phoneNo} is
 * exposed so a ride's driver can call the rider to coordinate pickup (mirrors
 * the driver summary, which exposes the driver's number for the same reason).
 */
public record PassengerSummaryResponse(
        UUID userId,
        String fullName,
        Double rating,
        String phoneNo
) {
}
