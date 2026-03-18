package com.saferide.profile_service.models.mappers;

import com.saferide.profile_service.models.dtos.VehicleRequest;
import com.saferide.profile_service.models.dtos.VehicleResponse;
import com.saferide.profile_service.models.entities.Vehicle;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface VehicleMapper {
    Vehicle toVehicle(VehicleRequest request);

    VehicleResponse toResponse(VehicleResponse response);
}
