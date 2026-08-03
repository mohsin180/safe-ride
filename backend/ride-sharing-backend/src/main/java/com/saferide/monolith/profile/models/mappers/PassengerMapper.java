package com.saferide.monolith.profile.models.mappers;

import com.saferide.monolith.profile.models.dtos.PassengerProfileRequest;
import com.saferide.monolith.profile.models.dtos.PassengerProfileResponse;
import com.saferide.monolith.profile.models.entities.PassengerProfile;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PassengerMapper {

    PassengerProfile toPassenger(PassengerProfileRequest request);

    PassengerProfileResponse toResponse(PassengerProfile profile);

    void updatePassenger(PassengerProfileRequest request, @MappingTarget PassengerProfile profile);
}
