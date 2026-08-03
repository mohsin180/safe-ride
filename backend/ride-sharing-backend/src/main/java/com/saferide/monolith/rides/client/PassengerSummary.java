package com.saferide.monolith.rides.client;

import java.util.UUID;

/**
 * Mirror of profile-service's {@code PassengerSummaryResponse}. Just the
 * fields rides-service needs to render a host / co-passenger card.
 */
public record PassengerSummary(
        UUID userId,
        String fullName,
        Double rating,
        String phoneNo
) {
}
