package com.saferide.monolith.user.model;

import com.saferide.monolith.user.model.dtos.RegisterRequest;
import com.saferide.monolith.user.model.dtos.UserResponse;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UserMapper {
    Users toUser(RegisterRequest request);

    UserResponse toResponse(Users users);
}
