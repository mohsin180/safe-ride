package com.saferide.user_service.services;

import com.saferide.user_service.exceptions.ProfileAlreadyExistsException;
import com.saferide.user_service.exceptions.ProfileNotFoundException;
import com.saferide.user_service.exceptions.UserNotFoundException;
import com.saferide.user_service.model.dtos.ProfileRequest;
import com.saferide.user_service.model.dtos.ProfileResponse;
import com.saferide.user_service.model.entities.PassengerProfile;
import com.saferide.user_service.model.entities.Users;
import com.saferide.user_service.model.mappers.ProfileMapper;
import com.saferide.user_service.repos.ProfileRepository;
import com.saferide.user_service.repos.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Service
public class ProfileService {

    private final ProfileMapper profileMapper;
    private final FileStorageService storageService;
    private final ProfileRepository profileRepository;
    private final UserRepository userRepository;

    public ProfileService(ProfileMapper profileMapper, FileStorageService storageService, ProfileRepository profileRepository, UserRepository userRepository) {
        this.profileMapper = profileMapper;
        this.storageService = storageService;
        this.profileRepository = profileRepository;
        this.userRepository = userRepository;
    }

    public ProfileResponse createProfile(ProfileRequest request, MultipartFile image, String userId) {
        if (profileRepository.existsByUserId(UUID.fromString(userId))) {
            throw new ProfileAlreadyExistsException("Profile already exists");
        }
        Users users = userRepository.findById(UUID.fromString(userId)).orElseThrow(
                () -> new UserNotFoundException("User not found with id: " + userId)
        );
        PassengerProfile passengerProfile = profileMapper.toProfile(request);
        String url = null;
        if (image != null && !image.isEmpty()) {
            url = storageService.storeProfileImage(image, UUID.fromString(userId));
        }
        passengerProfile.setUsers(users);
        passengerProfile.setProfilePicture(url);
        profileRepository.save(passengerProfile);
        return profileMapper.toResponse(passengerProfile);
    }


    public ProfileResponse getProfileById(String userId) {
        PassengerProfile passengerProfile = profileRepository.findByUserId(UUID.fromString(userId)).orElseThrow(
                () -> new ProfileNotFoundException("Profile not found with id: " + userId)
        );
        return profileMapper.toResponse(passengerProfile);
    }

    public ProfileResponse updateProfile(ProfileRequest request, MultipartFile image, String id) {
        PassengerProfile passengerProfile = profileRepository.findByUserId(UUID.fromString(id)).orElseThrow(
                () -> new ProfileNotFoundException("Profile not found with id: " + id)
        );
        profileMapper.updateProfileFromRequest(request, passengerProfile);
        if (image != null && !image.isEmpty()) {
            String url = storageService.storeProfileImage(image, UUID.fromString(id));
            passengerProfile.setProfilePicture(url);
        }
        profileRepository.save(passengerProfile);
        return profileMapper.toResponse(passengerProfile);
    }
}
