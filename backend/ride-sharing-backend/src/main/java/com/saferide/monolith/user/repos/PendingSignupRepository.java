package com.saferide.monolith.user.repos;

import com.saferide.monolith.user.model.PendingSignup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PendingSignupRepository extends JpaRepository<PendingSignup, UUID> {

    Optional<PendingSignup> findByEmail(String email);

    boolean existsByEmail(String email);

    /**
     * Abandoned signups, for the sweeper. Anyone who never made it past KYC
     * within the retention window frees their email address again — otherwise
     * a stalled attempt would block that address from ever registering.
     */
    java.util.List<PendingSignup> findByCreatedAtBefore(Instant cutoff);
}
