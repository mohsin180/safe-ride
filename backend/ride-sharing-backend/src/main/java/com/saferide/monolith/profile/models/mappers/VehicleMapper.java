package com.saferide.monolith.profile.models.mappers;

import com.saferide.monolith.profile.models.dtos.VehicleRequest;
import com.saferide.monolith.profile.models.dtos.VehicleResponse;
import com.saferide.monolith.profile.models.entities.Vehicle;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface VehicleMapper {
    Vehicle toVehicle(VehicleRequest request);

    VehicleResponse toResponse(VehicleResponse response);

    @Mapping(target = "driverProfile", ignore = true)
    void updateVehicle(VehicleRequest request, @MappingTarget Vehicle vehicle);
}
