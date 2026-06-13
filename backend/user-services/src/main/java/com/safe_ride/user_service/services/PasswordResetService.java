package com.safe_ride.user_service.services;

import com.safe_ride.user_service.exceptions.InvalidTokenException;
import com.safe_ride.user_service.model.PasswordResetToken;
import com.safe_ride.user_service.model.Users;
import com.safe_ride.user_service.repos.PasswordResetTokenRepo;
import com.safe_ride.user_service.repos.UserRepository;
import com.safe_ride.user_service.security.HashedVerificationToken;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@Slf4j
public class PasswordResetService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepo tokenRepo;
    private final MailService mailService;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.reset.token-expiry-hours}")
    private int tokenExpiryHours;

    public PasswordResetService(UserRepository userRepository,
                                PasswordResetTokenRepo tokenRepo,
                                MailService mailService,
                                PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.tokenRepo = tokenRepo;
        this.mailService = mailService;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Issues a reset link for the given email. Deliberately silent when the
     * email is unknown — returning the same "ok" either way prevents account
     * enumeration (an attacker can't probe which emails are registered).
     */
    @Transactional
    public void createAndSendResetLink(String email) {
        Optional<Users> maybeUser = userRepository.findByEmail(email);
        if (maybeUser.isEmpty()) {
            log.info("Password reset requested for unknown email — ignoring silently");
            return;
        }
        Users user = maybeUser.get();

        // One live token per user — kill any previous unused ones first.
        tokenRepo.invalidateUnusedTokensByUserId(user.getId());

        String rawToken = HashedVerificationToken.generateRawToken();
        String hashToken = HashedVerificationToken.hashToken(rawToken);
        PasswordResetToken resetToken = PasswordResetToken.builder()
                .users(user)
                .tokenHash(hashToken)
                .expiresAt(LocalDateTime.now().plusHours(tokenExpiryHours))
                .used(false)
                .build();
        tokenRepo.save(resetToken);

        mailService.sendPasswordResetEmail(user.getEmail(), rawToken);
        log.info("Password reset token created for user id={}", user.getId());
    }

    /**
     * Cheap, side-effect-free check the reset screen calls before showing the
     * new-password form: is this token real, unused, and unexpired?
     */
    public boolean isResetTokenValid(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return false;
        }
        String tokenHash = HashedVerificationToken.hashToken(rawToken);
        return tokenRepo.findByTokenHash(tokenHash)
                .filter(t -> !t.isUsed())
                .filter(t -> t.getExpiresAt().isAfter(LocalDateTime.now()))
                .isPresent();
    }

    /**
     * Consumes the token and sets the new password. Throws
     * {@link InvalidTokenException} for an unknown / used / expired token.
     */
    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        String tokenHash = HashedVerificationToken.hashToken(rawToken);
        PasswordResetToken resetToken = tokenRepo.findByTokenHash(tokenHash)
                .orElseThrow(() -> {
                    log.error("Password reset attempted with unknown token hash");
                    return new InvalidTokenException("Invalid or expired reset token");
                });
        if (resetToken.isUsed()) {
            log.error("Password reset attempted with already-used token id={}", resetToken.getId());
            throw new InvalidTokenException("Invalid or expired reset token");
        }
        if (resetToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            log.error("Password reset attempted with expired token id={}", resetToken.getId());
            throw new InvalidTokenException("Invalid or expired reset token");
        }

        resetToken.setUsed(true);
        tokenRepo.save(resetToken);

        Users user = resetToken.getUsers();
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        log.info("Password reset successfully for user id={}", user.getId());
    }
}
