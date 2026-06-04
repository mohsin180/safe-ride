package com.safe_ride.user_service.services;

import com.safe_ride.user_service.exceptions.InvalidTokenException;
import com.safe_ride.user_service.exceptions.UserNotFoundException;
import com.safe_ride.user_service.model.EmailVerificationToken;
import com.safe_ride.user_service.model.Users;
import com.safe_ride.user_service.repos.EmailVerificationTokenRepo;
import com.safe_ride.user_service.repos.UserRepository;
import com.safe_ride.user_service.security.HashedVerificationToken;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@Slf4j
public class EmailVerificationService {

    private final UserRepository userRepository;
    private final MailService mailService;
    private final EmailVerificationTokenRepo tokenRepo;
    @Value("${app.verification.token-expiry-hours}")
    private int tokenExpiry;

    public EmailVerificationService(UserRepository userRepository, MailService mailService, EmailVerificationTokenRepo tokenRepo) {
        this.userRepository = userRepository;
        this.mailService = mailService;
        this.tokenRepo = tokenRepo;
    }

    @Transactional
    public void createAndSendVerification(Users users) {
        String rawToken = HashedVerificationToken.generateRawToken();
        String hashToken = HashedVerificationToken.hashToken(rawToken);
        EmailVerificationToken verificationToken = EmailVerificationToken
                .builder()
                .users(users)
                .tokenHash(hashToken)
                .expiresAt(LocalDateTime.now().plusHours(tokenExpiry))
                .used(false)
                .build();
        tokenRepo.save(verificationToken);
        mailService.sendEmail(users.getEmail(), rawToken);
        log.info("Verification token created for user id={}", users.getId());
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
        Users users = verificationToken.getUsers();
        users.setEmailVerified(true);
        userRepository.save(users);
        log.info("Email verified successfully for user id={}", users.getId());
    }

    @Transactional
    public void resendVerificationEmail(String email) {
        Users users = userRepository.findByEmail(email).orElseThrow(
                () -> new UserNotFoundException("Email was not found")
        );
        if (users.isEmailVerified()) {
            log.debug("Resend verification requested for already-verified user id={}", users.getId());
            return;
        }
        tokenRepo.invalidateUnusedTokensByUserId(users.getId());
        log.warn("Invalidated old unused tokens for user id={}", users.getId());
        createAndSendVerification(users);
    }
}
