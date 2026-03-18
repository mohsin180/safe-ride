package com.saferide.profile_service.controllers;

import com.saferide.profile_service.models.dtos.DriverProfileRequest;
import com.saferide.profile_service.models.dtos.DriverProfileResponse;
import com.saferide.profile_service.service.DriverProfileService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/profile")
public class DriverProfileController {
    private final DriverProfileService profileService;

    public DriverProfileController(DriverProfileService profileService) {
        this.profileService = profileService;
    }

    @PreAuthorize("hasRole('DRIVER')")
    @PostMapping("/driver")
    public ResponseEntity<DriverProfileResponse> createDriverProfile(
            @Valid @RequestBody DriverProfileRequest request) {
        DriverProfileResponse response = profileService.createDriverProfile(request);
        return ResponseEntity.ok(response);
    }
}
