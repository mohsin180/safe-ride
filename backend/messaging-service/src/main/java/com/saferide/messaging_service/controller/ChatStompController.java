package com.saferide.messaging_service.controller;

import com.saferide.messaging_service.model.dtos.SendMessageRequest;
import com.saferide.messaging_service.service.ChatService;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.UUID;

/**
 * Handles real-time sends. A client publishes to {@code /app/chat/<rideId>};
 * the service persists the message and broadcasts it to the ride's topic,
 * so every subscribed member (sender included) receives it.
 */
@Controller
public class ChatStompController {

    private final ChatService chatService;

    public ChatStompController(ChatService chatService) {
        this.chatService = chatService;
    }

    @MessageMapping("/chat/{rideId}")
    public void send(@DestinationVariable UUID rideId,
                     SendMessageRequest payload,
                     Principal principal) {
        if (principal == null || payload == null) {
            return;
        }
        chatService.postMessage(rideId, UUID.fromString(principal.getName()), payload.text());
    }
}
