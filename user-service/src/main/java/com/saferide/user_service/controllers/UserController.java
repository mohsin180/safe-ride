package com.saferide.user_service.controllers;

import com.saferide.user_service.model.dtos.LoginRequest;
import com.saferide.user_service.model.dtos.LoginResponse;
import com.saferide.user_service.model.dtos.RegisterRequest;
import com.saferide.user_service.model.dtos.RegisterResponse;
import com.saferide.user_service.services.UserService;
import com.saferide.user_service.services.keycloak.KeycloakAdminClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/user")
public class UserController {

    private final UserService userService;
    private final KeycloakAdminClient keycloakAdminClient;

    @Autowired
    public UserController(UserService userService, KeycloakAdminClient keycloakAdminClient) {
        this.userService = userService;
        this.keycloakAdminClient = keycloakAdminClient;
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> createUser(@RequestBody RegisterRequest request) {
        RegisterResponse response = userService.createUser(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> loginUser(@RequestBody LoginRequest request) {
        LoginResponse response = userService.loginUser(request);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{userId}/verify-email")
    public ResponseEntity<Void> verifyEmail(@PathVariable String userId) {
        keycloakAdminClient.verifyEmail(userId);
        return ResponseEntity.ok().build();
    }
}
