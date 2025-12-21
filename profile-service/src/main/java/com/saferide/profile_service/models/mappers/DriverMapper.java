package com.saferide.profile_service.models.mappers;

import com.saferide.profile_service.models.dtos.DriverProfileRequest;
import com.saferide.profile_service.models.dtos.DriverProfileResponse;
import com.saferide.profile_service.models.entities.DriverProfile;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface DriverMapper {

    DriverProfile toDriver(DriverProfileRequest request);

    DriverProfileResponse toDriverResponse(DriverProfile profile);
}
