package com.saferide.monolith.profile.models.mappers;

import com.saferide.monolith.profile.models.dtos.DriverProfileRequest;
import com.saferide.monolith.profile.models.dtos.DriverProfileResponse;
import com.saferide.monolith.profile.models.entities.DriverProfile;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface DriverMapper {

    DriverProfile toDriver(DriverProfileRequest request);

    DriverProfileResponse toDriverResponse(DriverProfile profile);

    @Mapping(target = "vehicle", ignore = true)
    void updateDriver(DriverProfileRequest request, @MappingTarget DriverProfile profile);
}
