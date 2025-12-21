package com.saferide.profile_service.service;

import com.saferide.profile_service.exceptions.ProfileNotFoundException;
import com.saferide.profile_service.models.dtos.DriverProfileRequest;
import com.saferide.profile_service.models.dtos.DriverProfileResponse;
import com.saferide.profile_service.models.dtos.PassengerProfileRequest;
import com.saferide.profile_service.models.dtos.PassengerProfileResponse;
import com.saferide.profile_service.models.entities.DriverProfile;
import com.saferide.profile_service.models.entities.PassengerProfile;
import com.saferide.profile_service.models.mappers.DriverMapper;
import com.saferide.profile_service.models.mappers.PassengerMapper;
import com.saferide.profile_service.repos.DriverProfileRepository;
import com.saferide.profile_service.repos.PassengerProfileRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class ProfileService {
    private final PassengerProfileRepository passengerRepo;
    private final DriverProfileRepository driverRepo;
    private final PassengerMapper passengerMapper;
    private final DriverMapper driverMapper;

    public ProfileService(PassengerProfileRepository passengerRepo, DriverProfileRepository driverRepo, PassengerMapper passengerMapper, DriverMapper driverMapper) {
        this.passengerRepo = passengerRepo;
        this.driverRepo = driverRepo;
        this.passengerMapper = passengerMapper;
        this.driverMapper = driverMapper;
    }

    // passenger profile methods
    public PassengerProfileResponse createPassengerProfile(String userId, PassengerProfileRequest request) {
        if (passengerRepo.existsByUserId(UUID.fromString(userId))) {
            throw new RuntimeException();
        }
        PassengerProfile passenger = passengerMapper.toPassenger(request);
        passenger.setUserId(UUID.fromString(userId));
        passengerRepo.save(passenger);
        return passengerMapper.toResponse(passenger);
    }

    public PassengerProfileResponse getProfileById(String id) {
        PassengerProfile profile = getProfile(id);
        return passengerMapper.toResponse(profile);
    }

    private PassengerProfile getProfile(String id) {
        return passengerRepo.findById(UUID.fromString(id)).orElseThrow(
                () -> new ProfileNotFoundException("Profile not found")
        );
    }

    public PassengerProfileResponse updatePassengerProfile(String id,
                                                           PassengerProfileRequest request) {
        return null;
    }


    // driver profile methods
    public DriverProfileResponse createDriverProfile(String userId, DriverProfileRequest request) {
        if (driverRepo.existsByUserId(UUID.fromString(userId))) {
            throw new RuntimeException();
        }
        DriverProfile driver = driverMapper.toDriver(request);
        driver.setUserId(UUID.fromString(userId));
        driverRepo.save(driver);
        return driverMapper.toDriverResponse(driver);
    }

    public DriverProfileResponse getDriverProfileById(String id) {
        DriverProfile driverProfile = getDriverProfile(id);
        return driverMapper.toDriverResponse(driverProfile);
    }

    private DriverProfile getDriverProfile(String id) {
        return driverRepo.findById(UUID.fromString(id)).orElseThrow(
                () -> new ProfileNotFoundException("Driver not found")
        );
    }


    public DriverProfileResponse updateDriverProfile(String id, DriverProfileResponse request) {
        return null;
    }
}
