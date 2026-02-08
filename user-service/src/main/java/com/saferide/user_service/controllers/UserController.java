package com.saferide.user_service.controllers;

import com.saferide.user_service.model.dtos.*;
import com.saferide.user_service.services.UserService;
import com.saferide.user_service.services.keycloak.KeycloakAdminClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

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

    @GetMapping("/{userId}/is-Email-Verified")
    public ResponseEntity<Boolean> isEmailVerified(@PathVariable String userId) {
        boolean emailVerified = keycloakAdminClient.isEmailVerified(userId);
        return ResponseEntity.ok(emailVerified);
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        keycloakAdminClient.sendResetPassword(email);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@RequestBody ResetPasswordRequest request) {
        keycloakAdminClient.updatePassword(request);
        return ResponseEntity.ok().build();
    }
}
