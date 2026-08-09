package com.saferide.monolith.user.controller;

import com.saferide.monolith.user.model.dtos.*;
import com.saferide.monolith.user.services.EmailVerificationService;
import com.saferide.monolith.user.services.PasswordResetService;
import com.saferide.monolith.user.services.UserService;
import jakarta.validation.Valid;
import org.apache.coyote.BadRequestException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class UserController {
    private final UserService userService;
    private final EmailVerificationService emailVerificationService;
    private final PasswordResetService passwordResetService;

    public UserController(UserService userService,
                          EmailVerificationService emailVerificationService,
                          PasswordResetService passwordResetService) {
        this.userService = userService;
        this.emailVerificationService = emailVerificationService;
        this.passwordResetService = passwordResetService;
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

    /**
     * Finishes signup. Authorised by the onboarding token from /register or
     * from a login that reported {@code roleRequired} — not by a user id in
     * the URL, which anyone could supply.
     */
    @PostMapping("/select-role")
    public ResponseEntity<LoginResponse> selectRole(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody RoleSelection role) {
        LoginResponse response = userService.selectRole(stripBearer(authorization), role);
        return ResponseEntity.ok(response);
    }

    private String stripBearer(String authorization) {
        if (authorization == null) {
            return "";
        }
        return authorization.startsWith("Bearer ") ? authorization.substring(7).trim() : authorization.trim();
    }

    /**
     * The page a password-reset email links to. Mail can only carry an
     * https URL, and the reset itself happens in the app — so this is the
     * bridge: it checks the token is still good, then hands the browser off
     * to the app's {@code saferide://} deep link.
     *
     * <p>Without it the link pointed at a path nothing served and every user
     * got the browser's "this page isn't working".
     */
    @GetMapping(value = "/reset-password", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> resetPasswordBridge(@RequestParam String token) {
        if (!passwordResetService.isResetTokenValid(token)) {
            return ResponseEntity.badRequest()
                    .contentType(MediaType.TEXT_HTML)
                    .body(errorHtml("This reset link has expired or has already been used. "
                            + "Request a new one from the app."));
        }
        return ResponseEntity.ok(openAppHtml(token));
    }

    @GetMapping(value = "/verify-email", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> verifyEmail(@RequestParam String token) {
        try {
            emailVerificationService.verifyEmail(token);
            return ResponseEntity.ok(successHtml());
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .contentType(MediaType.TEXT_HTML)
                    .body(errorHtml(e.getMessage()));
        }
    }

    private String successHtml() {
        return """
                <!DOCTYPE html>
                <html><head><meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>Email verified</title></head>
                <body style="font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;
                             background:#f4f4f7; margin:0; padding:40px; text-align:center;">
                  <div style="background:#fff; max-width:480px; margin:0 auto; padding:40px 30px;
                              border-radius:8px; box-shadow:0 2px 8px rgba(0,0,0,0.08);">
                    <h1 style="color:#1a73e8; margin:0 0 12px;">Email verified</h1>
                    <p style="color:#555; font-size:16px; line-height:1.5;">
                      You're all set. Return to the SafeRide app — it will continue
                      automatically within a few seconds.
                    </p>
                  </div>
                </body></html>
                """;
    }

    private String errorHtml(String message) {
        return """
                <!DOCTYPE html><html><body style="font-family:sans-serif; padding:40px; text-align:center;">
                  <h2 style="color:#c00;">Verification failed</h2>
                  <p>%s</p>
                  <p style="color:#888; font-size:14px;">Please request a new verification email from the app.</p>
                </body></html>
                """.formatted(message == null ? "Invalid or expired token." : message);
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

    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        // Always 200 — never reveal whether the email is registered.
        passwordResetService.createAndSendResetLink(request.email());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/reset/status")
    public ResponseEntity<Boolean> resetStatus(@RequestParam String token) {
        return ResponseEntity.ok(passwordResetService.isResetTokenValid(token));
    }

    /**
     * Corrects the gender chosen at signup. Returns a new token — the old one
     * still carries the old claim, so the app must replace it.
     */
    @PutMapping("/gender")
    public ResponseEntity<LoginResponse> changeGender(
            @Valid @RequestBody GenderChangeRequest request) {
        return ResponseEntity.ok(userService.changeGender(request));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.ok().build();
    }

    /**
     * Bounces the browser into the app at the new-password screen. The
     * redirect fires immediately; the button is there for the case where the
     * browser blocks an automatic scheme jump, and the note for the case
     * where the app isn't installed on the device reading the mail.
     */
    private String openAppHtml(String token) {
        String deepLink = "saferide://reset-password?token=" + token;
        return """
                <!DOCTYPE html>
                <html><head><meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>Reset your password</title>
                <meta http-equiv="refresh" content="0; url=%s"></head>
                <body style="font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;
                             background:#F8F9FB; margin:0; padding:40px; text-align:center;">
                  <div style="background:#fff; max-width:480px; margin:0 auto; padding:40px 30px;
                              border-radius:20px; box-shadow:0 10px 30px rgba(62,43,141,0.10);">
                    <h2 style="color:#1E2157; margin:0 0 12px;">Opening SafeRide…</h2>
                    <p style="color:#757575; margin:0 0 28px; line-height:1.5;">
                      Continue in the app to choose a new password.
                    </p>
                    <a href="%s" style="display:inline-block; background:#3E2B8D; color:#fff;
                       text-decoration:none; padding:14px 28px; border-radius:22px; font-weight:600;">
                      Open SafeRide
                    </a>
                    <p style="color:#9a9aa8; font-size:13px; margin:28px 0 0;">
                      Nothing happened? Open this link on the phone with SafeRide installed.
                    </p>
                  </div>
                  <script>location.replace("%s");</script>
                </body></html>
                """
                .formatted(deepLink, deepLink, deepLink);
    }
}
