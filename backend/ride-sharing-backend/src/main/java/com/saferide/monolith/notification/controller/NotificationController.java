package com.saferide.monolith.notification.controller;

import com.saferide.monolith.notification.model.dtos.NotificationResponse;
import com.saferide.monolith.notification.model.entity.Notification;
import com.saferide.monolith.notification.repo.NotificationRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * In-app notification API for the mobile client. There's no security layer
 * here — like the other downstream services, it trusts the gateway-injected
 * {@code X-User-Id} header to identify the recipient. Must only be reached
 * through the gateway.
 */
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationRepository repository;

    public NotificationController(NotificationRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public ResponseEntity<List<NotificationResponse>> list(
            @RequestHeader("X-User-Id") UUID userId) {
        List<NotificationResponse> items = repository
                .findByRecipientUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(items);
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> unreadCount(
            @RequestHeader("X-User-Id") UUID userId) {
        long count = repository.countByRecipientUserIdAndReadFalse(userId);
        return ResponseEntity.ok(Map.of("count", count));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markRead(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable("id") UUID id) {
        return repository.findByIdAndRecipientUserId(id, userId)
                .map(n -> {
                    if (!n.isRead()) {
                        n.setRead(true);
                        repository.save(n);
                    }
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/read-all")
    @Transactional
    public ResponseEntity<Void> markAllRead(@RequestHeader("X-User-Id") UUID userId) {
        repository.markAllRead(userId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable("id") UUID id) {
        return repository.findByIdAndRecipientUserId(id, userId)
                .map(n -> {
                    repository.delete(n);
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(
                n.getId(), n.getType(), n.getTitle(), n.getBody(),
                n.getRideId(), n.getSubjectUserId(), n.getSubjectName(),
                n.getSubjectRating(), n.getRequestId(), n.getPickup(), n.getDrop(),
                n.isRead(), n.getCreatedAt());
    }
}
