package com.saferide.user_service.model.mappers;

import com.saferide.user_service.model.dtos.ProfileRequest;
import com.saferide.user_service.model.dtos.ProfileResponse;
import com.saferide.user_service.model.entities.PassengerProfile;
import org.mapstruct.*;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ProfileMapper {
    PassengerProfile toProfile(ProfileRequest request);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateProfileFromRequest(
            ProfileRequest request,
            @MappingTarget PassengerProfile passengerProfile
    );

    ProfileResponse toResponse(PassengerProfile passengerProfile);
}
