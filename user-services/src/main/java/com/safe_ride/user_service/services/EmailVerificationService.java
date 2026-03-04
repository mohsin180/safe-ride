package com.safe_ride.user_service.services;

import com.safe_ride.user_service.model.Users;
import com.safe_ride.user_service.repos.EmailVerificationTokenRepo;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

@Service
public class EmailVerificationService {

    private final UserService userService;
    private final MailService mailService;
    private final EmailVerificationTokenRepo tokenRepo;

    public EmailVerificationService(UserService userService, MailService mailService, EmailVerificationTokenRepo tokenRepo) {
        this.userService = userService;
        this.mailService = mailService;
        this.tokenRepo = tokenRepo;
    }

    @Transactional
    public void createAndSendVerification(Users users) {

    }
}
