package com.saferide.monolith.profile.service;

import com.saferide.monolith.common.security.UserContext;
import com.saferide.monolith.kyc.service.KycIdentityWatch;
import com.saferide.monolith.profile.exceptions.ProfileAlreadyExistsException;
import com.saferide.monolith.profile.exceptions.ProfileNotFoundException;
import com.saferide.monolith.profile.exceptions.RoleNotAllowedException;
import com.saferide.monolith.profile.models.dtos.PassengerProfileRequest;
import com.saferide.monolith.profile.models.dtos.PassengerProfileResponse;
import com.saferide.monolith.profile.models.entities.PassengerProfile;
import com.saferide.monolith.profile.models.mappers.PassengerMapper;
import com.saferide.monolith.profile.repos.PassengerProfileRepository;
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

    public PassengerProfileResponse getMyProfile() {
        UserContext ctx = getCurrentUserContext();
        PassengerProfile profile = passengerProfileRepository
                .findByUserId(ctx.userId());
        if (profile == null) {
            throw new ProfileNotFoundException("Profile not found — complete onboarding first");
        }
        PassengerProfileResponse response = mapper.toResponse(profile);
        response.setEmail(ctx.email());
        response.setGender(ctx.gender());
        return response;
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
        String previousCnic = profile.getCnic();
        String previousName = profile.getFullName();
        mapper.updatePassenger(request, profile);
        // A verified badge belongs to the identity it was issued for.
        KycIdentityWatch.resetIfIdentityChanged(profile, previousCnic, previousName);
        PassengerProfile saved = passengerProfileRepository.save(profile);
        return mapper.toResponse(saved);
    }
}
