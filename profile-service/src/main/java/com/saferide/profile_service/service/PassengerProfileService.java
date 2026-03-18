package com.saferide.profile_service.service;

import com.saferide.profile_service.config.UserContext;
import com.saferide.profile_service.exceptions.ProfileAlreadyExistsException;
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
}
