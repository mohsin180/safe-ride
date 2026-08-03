package com.saferide.monolith.messaging.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Tracks how far a user has read in one ride's chat. Unread = messages
 * sent after {@code lastReadAt} by someone other than the user.
 */
@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "chat_read_state", uniqueConstraints = @UniqueConstraint(
        name = "uk_chat_read_user_ride", columnNames = {"user_id", "ride_id"}))
public class ChatReadState {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "ride_id", nullable = false)
    private UUID rideId;

    @Column(nullable = false)
    private Instant lastReadAt;
}
