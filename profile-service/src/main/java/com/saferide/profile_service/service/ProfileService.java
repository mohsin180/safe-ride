package com.saferide.profile_service.service;

import com.saferide.profile_service.models.dtos.PassengerProfileRequest;
import com.saferide.profile_service.models.dtos.PassengerProfileResponse;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class ProfileService {


    public PassengerProfileResponse createProfile(UUID userId, PassengerProfileRequest request) {
        return null;
    }
}
