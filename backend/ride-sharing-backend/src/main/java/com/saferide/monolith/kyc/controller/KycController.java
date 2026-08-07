package com.saferide.monolith.kyc.controller;

import com.saferide.monolith.kyc.model.KycStatusResponse;
import com.saferide.monolith.kyc.service.KycService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/kyc")
public class KycController {

    private final KycService kycService;

    public KycController(KycService kycService) {
        this.kycService = kycService;
    }

    /**
     * Starts (or restarts) a Didit verification session for the caller
     * (driver or passenger); returns the hosted URL to open.
     */
    @PreAuthorize("hasAnyRole('DRIVER','PASSENGER')")
    @PostMapping("/session")
    public ResponseEntity<KycStatusResponse> startVerification() {
        return ResponseEntity.ok(kycService.startVerification());
    }

    /** The caller's current KYC status (polls Didit while a session is live). */
    @PreAuthorize("hasAnyRole('DRIVER','PASSENGER')")
    @GetMapping("/status")
    public ResponseEntity<KycStatusResponse> getStatus() {
        return ResponseEntity.ok(kycService.getStatus());
    }
}
