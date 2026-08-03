package com.saferide.monolith.messaging.model.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body for sending a chat message (STOMP payload and REST fallback). */
public record SendMessageRequest(
        @NotBlank @Size(max = 2000) String text
) {
}
