package com.saferide.user_service.controllers;

import com.saferide.user_service.model.dtos.*;
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

    @GetMapping("/{userId}/is-Email-Verified")
    public ResponseEntity<Boolean> isEmailVerified(@PathVariable String userId) {
        boolean emailVerified = keycloakAdminClient.isEmailVerified(userId);
        return ResponseEntity.ok(emailVerified);
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<String> forgotPassword(@RequestBody ForgotPasswordDto request) {
        userService.sentResetLink(request.email());
        return ResponseEntity.ok("Reset link has been sent");
    }

    @PostMapping("/reset-password")
    public ResponseEntity<String> resetPassword(@RequestBody ResetPasswordRequest request) {
        userService.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.ok("Password updated successfully");
    }

    @GetMapping("/reset/click")
    public ResponseEntity<?> click(@RequestParam String token) {
        userService.markClicked(token);
        return ResponseEntity.ok("Email verified you can go back to app now.");
    }

    @GetMapping("/reset/status")
    public ResponseEntity<ResetStatusResponse> status(@RequestParam String token) {
        boolean verified = userService.isVerified(token);
        return ResponseEntity.ok(new ResetStatusResponse(verified));
    }

    @PostMapping("/assign-role")
    public ResponseEntity<Void> assignRole(@RequestBody AssignRoleRequest request,
                                           @RequestHeader("X-User-Id") String keycloakId) {
        keycloakAdminClient.assignRealmRole(keycloakId, request.role().name());
        return ResponseEntity.ok().build();
    }
}
