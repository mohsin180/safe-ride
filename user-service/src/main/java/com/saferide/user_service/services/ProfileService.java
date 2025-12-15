package com.saferide.user_service.services;

import com.saferide.user_service.model.dtos.ProfileRequest;
import com.saferide.user_service.model.dtos.ProfileResponse;
import com.saferide.user_service.model.entities.Profile;
import com.saferide.user_service.model.mappers.ProfileMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ProfileService {

    private final ProfileMapper profileMapper;

    public ProfileService(ProfileMapper profileMapper) {
        this.profileMapper = profileMapper;
    }

    public ProfileResponse createProfile(ProfileRequest request, MultipartFile image) {
        Profile profile = profileMapper.toProfile(request);
        return profileMapper.toResponse(profile);
    }


}
