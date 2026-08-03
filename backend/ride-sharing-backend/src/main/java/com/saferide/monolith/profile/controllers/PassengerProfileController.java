package com.saferide.monolith.profile.controllers;

import com.saferide.monolith.profile.models.dtos.PassengerProfileRequest;
import com.saferide.monolith.profile.models.dtos.PassengerProfileResponse;
import com.saferide.monolith.profile.service.PassengerProfileService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/profile")
public class PassengerProfileController {

    private final PassengerProfileService profileService;

    public PassengerProfileController(PassengerProfileService profileService) {
        this.profileService = profileService;
    }

    @PostMapping("/passenger")
    @PreAuthorize("hasRole('PASSENGER')")
    public ResponseEntity<PassengerProfileResponse> createPassengerProfile(
            @Valid @RequestBody PassengerProfileRequest request
    ) {
        PassengerProfileResponse response = profileService.createPassengerProfile(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/passenger")
    @PreAuthorize("hasRole('PASSENGER')")
    public ResponseEntity<PassengerProfileResponse> getMyProfile() {
        PassengerProfileResponse response = profileService.getMyProfile();
        return ResponseEntity.ok(response);
    }

    @PutMapping("/passenger")
    @PreAuthorize("hasRole('PASSENGER')")
    public ResponseEntity<PassengerProfileResponse> updatePassengerProfile(
            @Valid @RequestBody PassengerProfileRequest request
    ) {
        PassengerProfileResponse response = profileService.updateMyProfile(request);
        return ResponseEntity.ok(response);
    }
}
