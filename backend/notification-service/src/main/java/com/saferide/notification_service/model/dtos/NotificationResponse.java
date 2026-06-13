package com.saferide.notification_service.model.dtos;

import java.time.Instant;
import java.util.UUID;

/** One notification as the mobile client reads it. */
public record NotificationResponse(
        UUID id,
        String type,
        String title,
        String body,
        UUID rideId,
        UUID subjectUserId,
        String subjectName,
        Double subjectRating,
        UUID requestId,
        String pickup,
        String drop,
        boolean read,
        Instant createdAt
) {
}
