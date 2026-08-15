package com.saferide.monolith.user.services;

import com.saferide.monolith.user.model.PendingSignup;
import com.saferide.monolith.user.repos.EmailVerificationTokenRepo;
import com.saferide.monolith.user.repos.PendingSignupRepository;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Deletes signups nobody finished.
 *
 * <p>A pending row holds its email address hostage: registration refuses an
 * address that appears in either table, so someone who gave up halfway — or
 * mistyped their address — would otherwise be locked out of it forever, with
 * no account to recover and no way to start again. Sweeping unclaimed
 * attempts after the retention window hands the address back.
 */
@Component
@Slf4j
public class AbandonedSignupSweeper {

    private final PendingSignupRepository pendingRepo;
    private final EmailVerificationTokenRepo tokenRepo;
    private final int retentionDays;

    public AbandonedSignupSweeper(PendingSignupRepository pendingRepo,
                                  EmailVerificationTokenRepo tokenRepo,
                                  @Value("${app.signup.abandon-after-days:7}") int retentionDays) {
        this.pendingRepo = pendingRepo;
        this.tokenRepo = tokenRepo;
        this.retentionDays = retentionDays;
    }

    /** Hourly is far more often than needed for a days-long window, and cheap
     *  enough that a restart never leaves a long backlog. */
    @Scheduled(fixedDelay = 3_600_000L, initialDelay = 60_000L)
    @Transactional
    public void sweep() {
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        List<PendingSignup> abandoned = pendingRepo.findByCreatedAtBefore(cutoff);
        if (abandoned.isEmpty()) {
            return;
        }
        for (PendingSignup signup : abandoned) {
            // The FK from the verification tokens would refuse the delete.
            tokenRepo.deleteByPendingId(signup.getId());
        }
        pendingRepo.deleteAll(abandoned);
        log.info("Swept {} abandoned signup(s) older than {} days", abandoned.size(), retentionDays);
    }
}
