package com.saferide.messaging_service.model.dtos;

import java.time.Instant;
import java.util.UUID;

/** One chat message as the client renders it (sent over WS and REST). */
public record ChatMessageResponse(
        UUID id,
        UUID rideId,
        UUID senderId,
        String senderName,
        String text,
        Instant sentAt
) {
}
