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
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Service
public class ProfileService {
    private final PassengerProfileRepository passengerRepo;
    private final DriverProfileRepository driverRepo;
    private final PassengerMapper passengerMapper;
    private final DriverMapper driverMapper;
    private final FileStorageService fileStorageService;

    public ProfileService(PassengerProfileRepository passengerRepo, DriverProfileRepository driverRepo, PassengerMapper passengerMapper, DriverMapper driverMapper, FileStorageService fileStorageService) {
        this.passengerRepo = passengerRepo;
        this.driverRepo = driverRepo;
        this.passengerMapper = passengerMapper;
        this.driverMapper = driverMapper;
        this.fileStorageService = fileStorageService;
    }

    // passenger profile methods
    public PassengerProfileResponse createPassengerProfile(String userId,
                                                           PassengerProfileRequest request,
                                                           MultipartFile image) {
        if (passengerRepo.existsByUserId(UUID.fromString(userId))) {
            throw new RuntimeException();
        }
        PassengerProfile passenger = passengerMapper.toPassenger(request);
        passenger.setUserId(UUID.fromString(userId));
        if (image != null && !image.isEmpty()) {
            String imageUrl = fileStorageService.storeImage(image, UUID.fromString(userId),
                    "PASSENGER");
            passenger.setProfilePicture(imageUrl);
        }
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
                                                           PassengerProfileRequest request,
                                                           MultipartFile image) {
        PassengerProfile profile = getProfile(id);
        profile.setFullName(request.fullName());
        if (image != null && !image.isEmpty()) {
            String imageUrl = fileStorageService.storeImage(image, profile.getUserId(), "PASSENGER");
            profile.setProfilePicture(imageUrl);
        }
        passengerRepo.save(profile);
        return passengerMapper.toResponse(profile);
    }


    // driver profile methods
    public DriverProfileResponse createDriverProfile(String userId, DriverProfileRequest request, MultipartFile image) {
        if (driverRepo.existsByUserId(UUID.fromString(userId))) {
            throw new RuntimeException();
        }
        DriverProfile driver = driverMapper.toDriver(request);
        driver.setUserId(UUID.fromString(userId));
        if (image != null && !image.isEmpty()) {
            String imageUrl = fileStorageService.storeImage(image, UUID.fromString(userId), "DRIVER");
            driver.setProfilePicture(imageUrl);
        }
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


    public DriverProfileResponse updateDriverProfile(String id,
                                                     DriverProfileRequest request, MultipartFile image) {
        DriverProfile driver = getDriverProfile(id);
        driver.setFullName(request.fullName());
        driver.setVehicleModel(request.vehicleModel());
        driver.setVehicleNumber(request.vehicleNumber());
        driver.setLicenseNumber(request.licenseNumber());
        if (image != null && !image.isEmpty()) {
            String imageUrl = fileStorageService.storeImage(image, driver.getUserId(), "DRIVER");
            driver.setProfilePicture(imageUrl);
        }
        driverRepo.save(driver);
        return driverMapper.toDriverResponse(driver);
    }
}
