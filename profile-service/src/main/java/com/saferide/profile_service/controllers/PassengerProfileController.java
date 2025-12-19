package com.saferide.profile_service.controllers;

import com.saferide.profile_service.models.dtos.PassengerProfileRequest;
import com.saferide.profile_service.models.dtos.PassengerProfileResponse;
import com.saferide.profile_service.models.dtos.Role;
import com.saferide.profile_service.service.ProfileService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/profile")
public class PassengerProfileController {

    private final ProfileService profileService;

    public PassengerProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @PostMapping
    public ResponseEntity<PassengerProfileResponse> createPassengerProfile(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") Role role,
            @RequestBody PassengerProfileRequest request
    ) {
        if (role == Role.PASSENGER) {
            throw new RuntimeException("");
        }
        PassengerProfileResponse response = profileService.createProfile(userId, request);
        return ResponseEntity.ok(response);
    }
}
