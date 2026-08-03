package com.saferide.monolith.rides.client;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/**
 * Mirror of profile-service's {@code DriverSummaryResponse}. The fields
 * rides-service needs to show who drove a completed ride on the passenger
 * history screen, and to surface the assigned driver on the passenger's
 * active trip. {@code carInfo} is pre-composed by profile-service (e.g.
 * "Toyota Corolla · White"). {@code phone} lets the rider call the driver
 * mid-trip; it may be null if the driver profile is unreachable.
 */
public record DriverSummary(
        UUID userId,
        String fullName,
        String carInfo,
        Double rating,
        // profile-service serializes this as "phoneNo"; map it onto our
        // "phone" field so Feign/Jackson deserialization stays in sync.
        @JsonProperty("phoneNo") String phone
) {
}
