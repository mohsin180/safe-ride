package com.saferide.user_service.services;

import com.saferide.user_service.exceptions.UserNotFoundException;
import com.saferide.user_service.model.dtos.LoginRequest;
import com.saferide.user_service.model.dtos.LoginResponse;
import com.saferide.user_service.model.dtos.RegisterRequest;
import com.saferide.user_service.model.dtos.RegisterResponse;
import com.saferide.user_service.model.entities.PasswordResetToken;
import com.saferide.user_service.model.entities.Users;
import com.saferide.user_service.model.mappers.UserMapper;
import com.saferide.user_service.repos.PasswordResetTokenRepository;
import com.saferide.user_service.repos.UserRepository;
import com.saferide.user_service.services.keycloak.KeycloakAdminClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class UserService {
    private final KeycloakAdminClient adminClient;
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetTokenRepository tokenRepository;
    private final MailService mailService;

    public UserService(KeycloakAdminClient adminClient, UserRepository userRepository, UserMapper userMapper, PasswordEncoder passwordEncoder, PasswordResetTokenRepository tokenRepository, MailService mailService) {
        this.adminClient = adminClient;
        this.userRepository = userRepository;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.tokenRepository = tokenRepository;
        this.mailService = mailService;
    }

    public RegisterResponse createUser(RegisterRequest request) {
        String keycloakId = adminClient.createUserInKeycloak(request);
        String encodedPassword = passwordEncoder.encode(request.password());
        Users users = userMapper.toUser(request);
        users.setPassword(encodedPassword);
        users.setKeycloakId(keycloakId);
        userRepository.save(users);
        return userMapper.toResponse(users);
    }

    public LoginResponse loginUser(LoginRequest request) {
        return adminClient.loginUser(request);
    }

    public void sentResetLink(String email) {
        Users user = userRepository.findByEmail(email).orElseThrow(
                () -> new UserNotFoundException("Email not found")
        );
        String token = UUID.randomUUID().toString();
        PasswordResetToken prt = new PasswordResetToken();
        prt.setToken(token);
        prt.setUsers(user);
        prt.setExpiresAt(LocalDateTime.now().plusMinutes(15));
        prt.setUsed(false);
        tokenRepository.save(prt);

        String link = "http://localhost:8080/api/v1/user/reset/click?token=" + token;
        mailService.sendPasswordResetMail(user.getEmail(), link);
    }

    public void resetPassword(String token, String newPassword) {
        PasswordResetToken prt = tokenRepository.findByToken(token).orElseThrow(
                () -> new UserNotFoundException("Token not found")
        );
        if (prt.isUsed() || prt.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Token is expired");
        }
        Users users = prt.getUsers();
        String encode = passwordEncoder.encode(newPassword);
        users.setPassword(encode);
        userRepository.save(users);

        adminClient.updatePassword(users.getKeycloakId(), newPassword);

        prt.setUsed(true);
        tokenRepository.save(prt);
    }

    public void markClicked(String token) {
        PasswordResetToken prt = tokenRepository.findByToken(token).orElseThrow(
                () -> new UserNotFoundException("Token not found")
        );

        if (prt.isUsed() || prt.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Token is expired");
        }
        prt.setClicked(true);
        tokenRepository.save(prt);
    }


    public boolean isVerified(String token) {
        PasswordResetToken prt = tokenRepository.findByToken(token).orElseThrow(
                () -> new UserNotFoundException("Token not found")
        );
        if (prt.getExpiresAt().isBefore(LocalDateTime.now())) {
            return false;
        }
        return prt.isClicked() && !prt.isUsed();

    }
}
