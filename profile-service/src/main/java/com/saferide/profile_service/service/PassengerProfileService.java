package com.saferide.profile_service.service;

import com.saferide.profile_service.config.UserContext;
import com.saferide.profile_service.exceptions.ProfileAlreadyExistsException;
import com.saferide.profile_service.exceptions.ProfileNotFoundException;
import com.saferide.profile_service.exceptions.RoleNotAllowedException;
import com.saferide.profile_service.models.dtos.PassengerProfileRequest;
import com.saferide.profile_service.models.dtos.PassengerProfileResponse;
import com.saferide.profile_service.models.entities.PassengerProfile;
import com.saferide.profile_service.models.mappers.PassengerMapper;
import com.saferide.profile_service.repos.PassengerProfileRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class PassengerProfileService {
    private final PassengerMapper mapper;
    private final PassengerProfileRepository passengerProfileRepository;

    public PassengerProfileService(PassengerMapper mapper, PassengerProfileRepository passengerProfileRepository) {
        this.mapper = mapper;
        this.passengerProfileRepository = passengerProfileRepository;
    }

    public PassengerProfileResponse createPassengerProfile(
            PassengerProfileRequest request) {
        UserContext ctx = getCurrentUserContext();
        if (!"PASSENGER".equals(ctx.role())) {
            throw new RoleNotAllowedException("Only users with PASSENGER role can create a passenger profile");
        }
        if (passengerProfileRepository.existsByUserId(ctx.userId())) {
            throw new ProfileAlreadyExistsException("Profile Already exists");
        }
        PassengerProfile passenger = mapper.toPassenger(request);
        passenger.setUserId(ctx.userId());
        PassengerProfile saved = passengerProfileRepository.save(passenger);
        return mapper.toResponse(saved);
    }

    private UserContext getCurrentUserContext() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assert authentication != null;
        return (UserContext) authentication.getDetails();
    }

    public PassengerProfileResponse getMyProfile(String userId) {
        UserContext ctx = getCurrentUserContext();
        PassengerProfile profile = passengerProfileRepository
                .findByUserId(ctx.userId());
        return mapper.toResponse(profile);
    }

    public PassengerProfileResponse updateMyProfile(PassengerProfileRequest request) {
        UserContext ctx = getCurrentUserContext();
        if (!"PASSENGER".equals(ctx.role())) {
            throw new RoleNotAllowedException("Only users with PASSENGER role can update a passenger profile");
        }
        PassengerProfile profile = passengerProfileRepository.findByUserId(ctx.userId());
        if (profile == null) {
            throw new ProfileNotFoundException("Passenger profile not found");
        }
        mapper.updatePassenger(request, profile);
        PassengerProfile saved = passengerProfileRepository.save(profile);
        return mapper.toResponse(saved);
    }
}
