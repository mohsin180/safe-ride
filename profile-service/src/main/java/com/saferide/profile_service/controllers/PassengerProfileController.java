package com.saferide.profile_service.controllers;

import com.saferide.profile_service.models.dtos.PassengerProfileRequest;
import com.saferide.profile_service.models.dtos.PassengerProfileResponse;
import com.saferide.profile_service.models.dtos.Role;
import com.saferide.profile_service.service.ProfileService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/profile")
public class PassengerProfileController {

    private final ProfileService profileService;

    public PassengerProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @PostMapping("/passenger")
    public ResponseEntity<PassengerProfileResponse> createPassengerProfile(
            @RequestHeader("X-User-Id") String userId,
            @RequestBody PassengerProfileRequest request
    ) {
        PassengerProfileResponse response = profileService.createPassengerProfile(userId, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/passenger/{id}")
    public ResponseEntity<PassengerProfileResponse> getProfileById(@PathVariable String id) {
        PassengerProfileResponse response = profileService.getProfileById(id);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/passenger/{id}/update")
    public ResponseEntity<PassengerProfileResponse> updatePassengerProfile(@PathVariable String id,
                                                                           @RequestPart PassengerProfileRequest request
    ) {
        PassengerProfileResponse response = profileService.updatePassengerProfile(id, request);
        return ResponseEntity.ok(response);
    }
}
