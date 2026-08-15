package com.saferide.monolith.user.repos;

import com.saferide.monolith.user.model.EmailVerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmailVerificationTokenRepo extends JpaRepository<EmailVerificationToken, UUID> {
    Optional<EmailVerificationToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("UPDATE EmailVerificationToken t SET t.used = true "
            + "WHERE t.pendingSignup.id = :pendingId AND t.used = false")
    void invalidateUnusedTokensByPendingId(@Param("pendingId") UUID pendingId);

    /**
     * Tokens belonging to a signup that's being deleted (promoted or swept).
     * The FK would otherwise refuse the delete.
     */
    @Modifying
    @Query("DELETE FROM EmailVerificationToken t WHERE t.pendingSignup.id = :pendingId")
    void deleteByPendingId(@Param("pendingId") UUID pendingId);
}
