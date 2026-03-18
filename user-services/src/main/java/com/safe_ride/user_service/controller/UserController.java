package com.safe_ride.user_service.controller;

import com.safe_ride.user_service.model.dtos.*;
import com.safe_ride.user_service.services.EmailVerificationService;
import com.safe_ride.user_service.services.UserService;
import jakarta.validation.Valid;
import org.apache.coyote.BadRequestException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class UserController {
    private final UserService userService;
    private final EmailVerificationService emailVerificationService;

    public UserController(UserService userService, EmailVerificationService emailVerificationService) {
        this.userService = userService;
        this.emailVerificationService = emailVerificationService;
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        UserResponse register = userService.register(request);
        return ResponseEntity.ok(register);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) throws BadRequestException {
        LoginResponse login = userService.login(request);
        return ResponseEntity.ok(login);
    }

    @PostMapping("/{id}/select-role")
    public ResponseEntity<LoginResponse> selectRole(@PathVariable String id,
                                                    @Valid @RequestBody RoleSelection role) {
        LoginResponse response = userService.selectRole(id, role);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/verify-email")
    public ResponseEntity<Void> verifyEmail(@RequestParam String token) {
        emailVerificationService.verifyEmail(token);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<Void> resendVerificationEmail(@RequestBody @Valid ResendVerificationRequest request) {
        emailVerificationService.resendVerificationEmail(request.email());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/is-email-verified/{userId}")
    public ResponseEntity<Boolean> isEmailVerified(@PathVariable String userId) {
        boolean response = userService.isEmailVerified(userId);
        return ResponseEntity.ok(response);
    }
}
