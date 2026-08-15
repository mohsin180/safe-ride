package com.saferide.monolith.user.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
public class EmailVerificationToken {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * The signup being verified — never a {@link Users}, because a row in that
     * table only exists after onboarding finished, and finishing requires a
     * verified email. So by construction there is no verified-user-needing-
     * verification case for this token to point at.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pending_signup_id", nullable = false)
    private PendingSignup pendingSignup;
    @Column(nullable = false, unique = true, name = "token_hash")
    private String tokenHash;
    @Column(nullable = false)
    @Builder.Default
    private boolean used = false;
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;
}
