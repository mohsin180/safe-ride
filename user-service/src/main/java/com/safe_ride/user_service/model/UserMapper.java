package com.safe_ride.user_service.model;

import com.safe_ride.user_service.model.dtos.RegisterRequest;
import com.safe_ride.user_service.model.dtos.UserResponse;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UserMapper {
    Users toUser(RegisterRequest request);

    UserResponse toResponse(Users users);
}
