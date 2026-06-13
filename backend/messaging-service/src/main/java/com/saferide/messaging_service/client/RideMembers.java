package com.saferide.messaging_service.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.UUID;

/** Mirror of rides-service {@code RideMembersResponse}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RideMembers(
        UUID rideId,
        String pickup,
        String drop,
        String status,
        List<UUID> memberIds
) {
}
