package com.safe_ride.rides_service.model.dtos;

import java.util.List;
import java.util.UUID;

/**
 * Internal projection of a ride's chat: its route + the userIds that make
 * up the chat (host + driver + co-passengers). Consumed by
 * messaging-service to authorize chat access and build the chat list.
 */
public record RideMembersResponse(
        UUID rideId,
        String pickup,
        String drop,
        String status,
        List<UUID> memberIds
) {
}
