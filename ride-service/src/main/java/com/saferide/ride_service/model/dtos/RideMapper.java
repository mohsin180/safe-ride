package com.saferide.ride_service.model.dtos;

import com.saferide.ride_service.model.entity.Ride;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface RideMapper {

    Ride toRide(RideRequest request);

    RideResponse toRideResponse(Ride ride);
}
