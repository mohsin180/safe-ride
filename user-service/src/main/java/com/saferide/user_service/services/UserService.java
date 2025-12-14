package com.saferide.user_service.services;

import com.saferide.user_service.model.dtos.LoginRequest;
import com.saferide.user_service.model.dtos.LoginResponse;
import com.saferide.user_service.model.dtos.RegisterRequest;
import com.saferide.user_service.model.dtos.RegisterResponse;
import com.saferide.user_service.model.entities.Users;
import com.saferide.user_service.model.mappers.UserMapper;
import com.saferide.user_service.repos.UserRepository;
import com.saferide.user_service.services.keycloak.KeycloakAdminClient;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class UserService {
    private final KeycloakAdminClient adminClient;
    private final UserRepository userRepository;
    private final UserMapper userMapper;

    public UserService(KeycloakAdminClient adminClient, UserRepository userRepository, UserMapper userMapper) {
        this.adminClient = adminClient;
        this.userRepository = userRepository;
        this.userMapper = userMapper;
    }

    public RegisterResponse createUser(RegisterRequest request) {
        String keycloakId = adminClient.createUserInKeycloak(request);
        Users users = userMapper.toUser(request);
        users.setKeycloakId(UUID.fromString(keycloakId));
        userRepository.save(users);
        return userMapper.toResponse(users);
    }

    public LoginResponse loginUser(LoginRequest request) {
        return adminClient.loginUser(request);
    }
}
