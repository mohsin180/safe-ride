package com.saferide.user_service.model.mappers;

import com.saferide.user_service.model.dtos.ProfileRequest;
import com.saferide.user_service.model.dtos.ProfileResponse;
import com.saferide.user_service.model.entities.Profile;
import org.mapstruct.*;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ProfileMapper {
    Profile toProfile(ProfileRequest request);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateProfileFromRequest(
            ProfileRequest request,
            @MappingTarget Profile profile
    );

    ProfileResponse toResponse(Profile profile);
}
