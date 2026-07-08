package com.safe_ride.rides_service.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * One user blocking another. Used to keep the two apart in matching — neither
 * sees the other's rides in the available feed, in either direction.
 */
@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "user_block", uniqueConstraints = @UniqueConstraint(
        name = "uk_user_block", columnNames = {"blocker_id", "blocked_id"}),
        indexes = {
        @Index(name = "idx_user_block_blocker", columnList = "blocker_id"),
        @Index(name = "idx_user_block_blocked", columnList = "blocked_id")
})
public class UserBlock {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "blocker_id", nullable = false)
    private UUID blockerId;

    @Column(name = "blocked_id", nullable = false)
    private UUID blockedId;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
