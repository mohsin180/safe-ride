package com.saferide.notification_service.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Consumer-side copy of the event published by rides-service. Field names
 * must match the producer's JSON. Deserialized by type inference (see
 * RabbitConfig) so the producer's __TypeId__ header is ignored.
 */
public record RideNotificationEvent(
        String type,
        UUID rideId,
        List<UUID> recipients,
        String pickup,
        String drop,
        Integer stars,
        UUID actorId,
        String actorName,
        UUID requestId,
        Double rating,
        Instant occurredAt
) {
}
