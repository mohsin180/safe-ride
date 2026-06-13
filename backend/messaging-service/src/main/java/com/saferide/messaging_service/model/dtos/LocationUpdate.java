package com.saferide.messaging_service.model.dtos;

import java.time.Instant;
import java.util.UUID;

/**
 * One live position on a ride. Used both ways: the client SENDs lat/lng
 * (+ its own role and optional bearing) to {@code /app/track/<rideId>};
 * the server stamps the authoritative {@code userId} + {@code at} and
 * broadcasts to {@code /topic/track.<rideId>} for every ride member.
 */
public record LocationUpdate(
        UUID userId,
        String role,
        double lat,
        double lng,
        Double bearing,
        Instant at
) {
}
