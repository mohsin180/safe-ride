package com.saferide.profile_service.models.mappers;

import com.saferide.profile_service.models.dtos.PassengerProfileRequest;
import com.saferide.profile_service.models.dtos.PassengerProfileResponse;
import com.saferide.profile_service.models.entities.PassengerProfile;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PassengerMapper {

    PassengerProfile toPassenger(PassengerProfileRequest request);

    PassengerProfileResponse toResponse(PassengerProfile profile);
}
