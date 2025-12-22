package com.saferide.profile_service.controllers;

import com.saferide.profile_service.models.dtos.DriverProfileRequest;
import com.saferide.profile_service.models.dtos.DriverProfileResponse;
import com.saferide.profile_service.models.dtos.Role;
import com.saferide.profile_service.service.ProfileService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/profile")
public class DriverProfileController {
    private final ProfileService profileService;

    public DriverProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @PostMapping("/driver")
    public ResponseEntity<DriverProfileResponse> createDriverProfile(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") Role role,
            @RequestPart DriverProfileRequest request,
            @RequestPart(required = false) MultipartFile image
    ) {
        if (role == Role.DRIVER) {
            throw new RuntimeException("");
        }
        DriverProfileResponse response = profileService.createDriverProfile(userId, request, image);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/driver/{id}")
    public ResponseEntity<DriverProfileResponse> getProfileById(@PathVariable String id) {
        DriverProfileResponse response = profileService.getDriverProfileById(id);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/driver/{id}/update")
    public ResponseEntity<DriverProfileResponse> updatePassengerProfile(@PathVariable String id,
                                                                        @RequestPart DriverProfileRequest request,
                                                                        @RequestPart(required = false) MultipartFile image) {
        DriverProfileResponse response = profileService.updateDriverProfile(id, request, image);
        return ResponseEntity.ok(response);
    }
}
