package com.saferide.monolith.user.services;

import com.saferide.monolith.user.exceptions.InvalidTokenException;
import com.saferide.monolith.user.exceptions.UserNotFoundException;
import com.saferide.monolith.user.security.AttemptLimiter;
import com.saferide.monolith.user.model.EmailVerificationToken;
import com.saferide.monolith.user.model.PendingSignup;
import com.saferide.monolith.user.repos.EmailVerificationTokenRepo;
import com.saferide.monolith.user.repos.PendingSignupRepository;
import com.saferide.monolith.user.security.HashedVerificationToken;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Email verification, which now happens entirely against a
 * {@link PendingSignup} — there is no verified-or-not state on a real account
 * to maintain, because an account is only created once its email was proven.
 */
@Service
@Slf4j
public class EmailVerificationService {

    private final PendingSignupRepository pendingSignupRepository;
    private final MailService mailService;
    private final EmailVerificationTokenRepo tokenRepo;
    private final AttemptLimiter attemptLimiter;
    @Value("${app.verification.token-expiry-hours}")
    private int tokenExpiry;

    public EmailVerificationService(PendingSignupRepository pendingSignupRepository,
                                    MailService mailService,
                                    EmailVerificationTokenRepo tokenRepo,
                                    AttemptLimiter attemptLimiter) {
        this.pendingSignupRepository = pendingSignupRepository;
        this.mailService = mailService;
        this.tokenRepo = tokenRepo;
        this.attemptLimiter = attemptLimiter;
    }

    @Transactional
    public void createAndSendVerification(PendingSignup signup) {
        String rawToken = HashedVerificationToken.generateRawToken();
        String hashToken = HashedVerificationToken.hashToken(rawToken);
        EmailVerificationToken verificationToken = EmailVerificationToken
                .builder()
                .pendingSignup(signup)
                .tokenHash(hashToken)
                .expiresAt(LocalDateTime.now().plusHours(tokenExpiry))
                .used(false)
                .build();
        tokenRepo.save(verificationToken);
        mailService.sendEmail(signup.getEmail(), rawToken);
        log.info("Verification token created for pending signup id={}", signup.getId());
    }

    @Transactional
    public void verifyEmail(String rawToken) {
        String tokenHash = HashedVerificationToken.hashToken(rawToken);
        EmailVerificationToken verificationToken = tokenRepo.findByTokenHash(tokenHash)
                .orElseThrow(() -> {
                    log.error("Verification attempted with unknown token hash");
                    return new InvalidTokenException("Token not Found");
                });
        if (verificationToken.isUsed()) {
            log.error("Verification attempted with already-used token id={}", verificationToken.getId());
            throw new InvalidTokenException("Token not Found");
        }
        if (verificationToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            log.error("Verification attempted with expired token id={}", verificationToken.getId());
            throw new InvalidTokenException("Token not Found");
        }
        verificationToken.setUsed(true);
        tokenRepo.save(verificationToken);
        PendingSignup signup = verificationToken.getPendingSignup();
        signup.setEmailVerified(true);
        pendingSignupRepository.save(signup);
        log.info("Email verified successfully for pending signup id={}", signup.getId());
    }

    @Transactional
    public void resendVerificationEmail(String email) {
        // Unbounded, this endpoint could flood an inbox and burn the app's
        // SMTP quota; the address is the natural budget key.
        attemptLimiter.check("resend the verification email", email);
        PendingSignup signup = pendingSignupRepository.findByEmail(email).orElseThrow(
                () -> new UserNotFoundException("Email was not found")
        );
        if (signup.isEmailVerified()) {
            log.debug("Resend verification requested for already-verified signup id={}", signup.getId());
            return;
        }
        tokenRepo.invalidateUnusedTokensByPendingId(signup.getId());
        log.warn("Invalidated old unused tokens for pending signup id={}", signup.getId());
        createAndSendVerification(signup);
    }
}
