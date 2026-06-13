package com.saferide.messaging_service.model.dtos;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** One row in the chat-list screen: a ride's chat with its last message. */
public record ChatSummaryResponse(
        UUID rideId,
        String title,
        List<String> memberNames,
        String lastMessage,
        String lastSenderName,
        Instant lastSentAt,
        long unreadCount
) {
}
