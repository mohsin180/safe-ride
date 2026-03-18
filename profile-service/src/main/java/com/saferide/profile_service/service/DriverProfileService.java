package com.saferide.profile_service.service;

import com.saferide.profile_service.config.UserContext;
import com.saferide.profile_service.exceptions.ProfileAlreadyExistsException;
import com.saferide.profile_service.exceptions.RoleNotAllowedException;
import com.saferide.profile_service.models.dtos.DriverProfileRequest;
import com.saferide.profile_service.models.dtos.DriverProfileResponse;
import com.saferide.profile_service.models.entities.DriverProfile;
import com.saferide.profile_service.models.entities.Vehicle;
import com.saferide.profile_service.models.mappers.DriverMapper;
import com.saferide.profile_service.models.mappers.VehicleMapper;
import com.saferide.profile_service.repos.DriverProfileRepository;
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

}
