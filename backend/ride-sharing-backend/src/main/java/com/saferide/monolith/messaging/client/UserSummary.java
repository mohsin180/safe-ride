package com.saferide.monolith.messaging.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

/**
 * Minimal projection of a profile (passenger or driver) — only what a chat
 * needs to show a sender name. Extra fields (rating, carInfo) are ignored.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UserSummary(
        UUID userId,
        String fullName
) {
}
