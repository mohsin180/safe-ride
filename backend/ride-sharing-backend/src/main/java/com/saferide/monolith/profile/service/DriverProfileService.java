package com.saferide.monolith.profile.service;

import com.saferide.monolith.common.security.UserContext;
import com.saferide.monolith.kyc.service.KycIdentityWatch;
import com.saferide.monolith.profile.exceptions.ProfileAlreadyExistsException;
import com.saferide.monolith.profile.exceptions.ProfileNotFoundException;
import com.saferide.monolith.profile.exceptions.RoleNotAllowedException;
import com.saferide.monolith.profile.models.dtos.DriverProfileRequest;
import com.saferide.monolith.profile.models.dtos.DriverProfileResponse;
import com.saferide.monolith.profile.models.entities.DriverProfile;
import com.saferide.monolith.profile.models.entities.Vehicle;
import com.saferide.monolith.profile.models.mappers.DriverMapper;
import com.saferide.monolith.profile.models.mappers.VehicleMapper;
import com.saferide.monolith.profile.repos.DriverProfileRepository;
import jakarta.transaction.Transactional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class DriverProfileService {
    private final DriverMapper driverMapper;
    private final VehicleMapper vehicleMapper;
    private final DriverProfileRepository driverProfileRepository;

    public DriverProfileService(DriverMapper driverMapper, VehicleMapper vehicleMapper, DriverProfileRepository driverProfileRepository) {
        this.driverMapper = driverMapper;
        this.vehicleMapper = vehicleMapper;
        this.driverProfileRepository = driverProfileRepository;
    }

    @Transactional
    public DriverProfileResponse createDriverProfile(DriverProfileRequest request) {

        UserContext ctx = getCurrentUserContext();
        if (!"DRIVER".equals(ctx.role())) {
            throw new RoleNotAllowedException("Only users with DRIVER role can create a driver profile");
        }
        if (driverProfileRepository.existsByUserId(ctx.userId())) {
            throw new ProfileAlreadyExistsException("Profile Already Exists");
        }
        DriverProfile driverProfile = driverMapper.toDriver(request);
        driverProfile.setUserId(ctx.userId());
        Vehicle vehicle = vehicleMapper.toVehicle(request.vehicle());
        vehicle.setDriverProfile(driverProfile);
        driverProfile.setVehicle(vehicle);
        DriverProfile savedProfile = driverProfileRepository.save(driverProfile);
        return driverMapper.toDriverResponse(savedProfile);
    }

    private UserContext getCurrentUserContext() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assert authentication != null;
        return (UserContext) authentication.getDetails();
    }

    public DriverProfileResponse getMyProfile() {
        UserContext ctx = getCurrentUserContext();
        DriverProfile profile = driverProfileRepository.findByUserId(ctx.userId());
        if (profile == null) {
            throw new ProfileNotFoundException("Profile not found — complete onboarding first");
        }
        DriverProfileResponse response = driverMapper.toDriverResponse(profile);
        response.setEmail(ctx.email());
        response.setGender(ctx.gender());
        return response;
    }

    @Transactional
    public DriverProfileResponse updateMyProfile(DriverProfileRequest request) {
        UserContext ctx = getCurrentUserContext();
        if (!"DRIVER".equals(ctx.role())) {
            throw new RoleNotAllowedException("Only users with DRIVER role can update a driver profile");
        }
        DriverProfile profile = driverProfileRepository.findByUserId(ctx.userId());
        if (profile == null) {
            throw new ProfileNotFoundException("Driver profile not found");
        }
        String previousCnic = profile.getCnic();
        String previousName = profile.getFullName();
        driverMapper.updateDriver(request, profile);
        vehicleMapper.updateVehicle(request.vehicle(), profile.getVehicle());
        // A verified badge belongs to the identity it was issued for.
        KycIdentityWatch.resetIfIdentityChanged(profile, previousCnic, previousName);
        DriverProfile saved = driverProfileRepository.save(profile);
        return driverMapper.toDriverResponse(saved);
    }
}
