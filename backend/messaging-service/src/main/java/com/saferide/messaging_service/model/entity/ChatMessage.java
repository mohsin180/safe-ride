package com.saferide.messaging_service.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * A single text message in a ride's group chat. The chat is identified by
 * the rideId — there's no separate group/membership table here;
 * rides-service is the source of truth for who belongs to a ride.
 */
@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "chat_message", indexes = {
        @Index(name = "idx_chat_message_ride", columnList = "ride_id")
})
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "ride_id", nullable = false)
    private UUID rideId;

    @Column(name = "sender_id", nullable = false)
    private UUID senderId;

    @Column(nullable = false, length = 2000)
    private String text;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant sentAt;
}
