package com.saferide.user_service.controllers;

import com.saferide.user_service.model.dtos.ProfileRequest;
import com.saferide.user_service.model.dtos.ProfileResponse;
import com.saferide.user_service.services.ProfileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/profile")
public class ProfileController {
    private final ProfileService profileService;

    @Autowired
    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @PostMapping(value = "/create", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProfileResponse> createProfile(@RequestPart ProfileRequest request,
                                                         @RequestPart(required = false) MultipartFile image,
                                                         @RequestParam("userId") String userId) {
        ProfileResponse response = profileService.createProfile(request, image, userId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProfileResponse> getProfileById(@PathVariable String userId) {
        ProfileResponse response = profileService.getProfileById(userId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/update")
    public ResponseEntity<ProfileResponse> updateProfile(@RequestPart ProfileRequest request,
                                                         @RequestPart(required = false) MultipartFile image,
                                                         @RequestParam("userId") String userId) {
        ProfileResponse response = profileService.updateProfile(request, image, userId);
        return ResponseEntity.ok(response);
    }
}
