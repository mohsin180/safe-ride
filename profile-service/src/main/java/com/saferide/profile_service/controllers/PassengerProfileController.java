package com.saferide.profile_service.controllers;

import com.saferide.profile_service.models.dtos.PassengerProfileRequest;
import com.saferide.profile_service.models.dtos.PassengerProfileResponse;
import com.saferide.profile_service.models.dtos.Role;
import com.saferide.profile_service.service.ProfileService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/profile")
public class PassengerProfileController {

    private final ProfileService profileService;

    public PassengerProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @PostMapping
    public ResponseEntity<PassengerProfileResponse> createPassengerProfile(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") Role role,
            @RequestBody PassengerProfileRequest request
    ) {
        if (role == Role.PASSENGER) {
            throw new RuntimeException("");
        }
        PassengerProfileResponse response = profileService.createPassengerProfile(userId, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PassengerProfileResponse> getProfileById(@PathVariable String id) {
        PassengerProfileResponse response = profileService.getProfileById(id);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/id/update")
    public ResponseEntity<PassengerProfileResponse> updatePassengerProfile(@PathVariable String id,
                                                                           @RequestBody PassengerProfileRequest request) {
        PassengerProfileResponse response = profileService.updatePassengerProfile(id, request);
        return ResponseEntity.ok(response);
    }
}
