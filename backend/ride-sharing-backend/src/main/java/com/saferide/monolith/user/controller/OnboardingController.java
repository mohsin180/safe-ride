package com.saferide.monolith.user.controller;

import com.saferide.monolith.profile.models.dtos.DriverProfileRequest;
import com.saferide.monolith.profile.models.dtos.PassengerProfileRequest;
import com.saferide.monolith.user.model.dtos.GenderChangeRequest;
import com.saferide.monolith.user.model.dtos.OnboardingStateResponse;
import com.saferide.monolith.user.services.OnboardingService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Everything a user does before they have an account.
 *
 * <p>Sits under {@code /api/v1/auth/**}, which is public, and is authorised
 * the same way {@code /select-role} always was: by the signed onboarding token
 * in the {@code Authorization} header, checked inside the service. It has to
 * be — the caller has no account, so there is no session for the security
 * filter to validate, which is precisely the property that keeps an unfinished
 * signup out of every other endpoint in the system.
 *
 * <p>Each call answers with the same {@link OnboardingStateResponse}, so the
 * app always knows the next screen without inferring it.
 */
@RestController
@RequestMapping("/api/v1/auth/onboarding")
public class OnboardingController {

    private final OnboardingService onboardingService;

    public OnboardingController(OnboardingService onboardingService) {
        this.onboardingService = onboardingService;
    }

    /** Where this signup left off — asked on every cold start. */
    @GetMapping("/state")
    public ResponseEntity<OnboardingStateResponse> state(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return ResponseEntity.ok(onboardingService.currentState(stripBearer(authorization)));
    }

    @PostMapping("/profile/passenger")
    public ResponseEntity<OnboardingStateResponse> passengerProfile(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody PassengerProfileRequest request) {
        return ResponseEntity.ok(
                onboardingService.savePassengerProfile(stripBearer(authorization), request));
    }

    @PostMapping("/profile/driver")
    public ResponseEntity<OnboardingStateResponse> driverProfile(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody DriverProfileRequest request) {
        return ResponseEntity.ok(
                onboardingService.saveDriverProfile(stripBearer(authorization), request));
    }

    /**
     * Corrects the gender picked at registration — the fix for the commonest
     * KYC rejection, where the scanned CNIC contradicts the account. Allowed
     * freely here because nothing has been verified yet; the equivalent call
     * on a real account refuses, since by then the value carries a checked
     * CNIC behind it.
     */
    @PostMapping("/gender")
    public ResponseEntity<OnboardingStateResponse> gender(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody GenderChangeRequest request) {
        return ResponseEntity.ok(
                onboardingService.changeGender(stripBearer(authorization), request));
    }

    @PostMapping("/kyc/session")
    public ResponseEntity<OnboardingStateResponse> startKyc(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return ResponseEntity.ok(onboardingService.startKyc(stripBearer(authorization)));
    }

    /**
     * Polls the verification. The response that first reports APPROVED is also
     * the one that carries the account's real token — promotion happens in the
     * same call, so there is no window where a verified user has no account.
     */
    @GetMapping("/kyc/status")
    public ResponseEntity<OnboardingStateResponse> kycStatus(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return ResponseEntity.ok(onboardingService.kycStatus(stripBearer(authorization)));
    }

    private String stripBearer(String authorization) {
        if (authorization == null) {
            return "";
        }
        return authorization.startsWith("Bearer ")
                ? authorization.substring(7).trim()
                : authorization.trim();
    }
}
