package com.saferide.monolith.messaging.controller;

import com.saferide.monolith.messaging.model.dtos.ChatMessageResponse;
import com.saferide.monolith.messaging.model.dtos.ChatSummaryResponse;
import com.saferide.monolith.messaging.model.dtos.SendMessageRequest;
import com.saferide.monolith.messaging.service.ChatService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST surface for chat: list chats, load history, unread count, mark read,
 * and a send fallback for when the WebSocket is briefly down. Identifies
 * the caller from the gateway-injected {@code X-User-Id} header — no
 * security layer (mirrors notification-service); reachable only via gateway.
 */
@RestController
@RequestMapping("/api/v1/messages")
public class ChatRestController {

    private final ChatService chatService;

    public ChatRestController(ChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping("/chats")
    public ResponseEntity<List<ChatSummaryResponse>> myChats(
            @RequestHeader("X-User-Id") UUID userId) {
        return ResponseEntity.ok(chatService.listChats(userId));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> unreadCount(
            @RequestHeader("X-User-Id") UUID userId) {
        return ResponseEntity.ok(Map.of("count", chatService.unreadCount(userId)));
    }

    @GetMapping("/{rideId}")
    public ResponseEntity<List<ChatMessageResponse>> history(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable("rideId") UUID rideId) {
        return ResponseEntity.ok(chatService.getHistory(rideId, userId));
    }

    @PostMapping("/{rideId}")
    public ResponseEntity<ChatMessageResponse> send(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable("rideId") UUID rideId,
            @Valid @RequestBody SendMessageRequest request) {
        return ResponseEntity.ok(chatService.postMessage(rideId, userId, request.text()));
    }

    @PostMapping("/{rideId}/read")
    public ResponseEntity<Void> markRead(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable("rideId") UUID rideId) {
        chatService.markRead(rideId, userId);
        return ResponseEntity.noContent().build();
    }
}
