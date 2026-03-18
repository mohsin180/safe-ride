package com.saferide.profile_service.controllers;

import com.saferide.profile_service.models.dtos.PassengerProfileRequest;
import com.saferide.profile_service.models.dtos.PassengerProfileResponse;
import com.saferide.profile_service.service.PassengerProfileService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
