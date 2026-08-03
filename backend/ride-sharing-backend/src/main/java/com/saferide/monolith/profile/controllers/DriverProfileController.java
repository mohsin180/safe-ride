package com.saferide.monolith.profile.controllers;

import com.saferide.monolith.profile.models.dtos.DriverProfileRequest;
import com.saferide.monolith.profile.models.dtos.DriverProfileResponse;
import com.saferide.monolith.profile.service.DriverProfileService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

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

    @PreAuthorize("hasRole('DRIVER')")
    @GetMapping("/driver")
    public ResponseEntity<DriverProfileResponse> getMyProfile() {
        DriverProfileResponse response = profileService.getMyProfile();
        return ResponseEntity.ok(response);
    }

    @PreAuthorize("hasRole('DRIVER')")
    @PutMapping("/driver")
    public ResponseEntity<DriverProfileResponse> updateDriverProfile(
            @Valid @RequestBody DriverProfileRequest request) {
        DriverProfileResponse response = profileService.updateMyProfile(request);
        return ResponseEntity.ok(response);
    }
}
