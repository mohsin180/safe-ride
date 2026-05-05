package com.safe_ride.rides_service.model.mapper;

import com.safe_ride.rides_service.model.dtos.CreateRideRequest;
import com.safe_ride.rides_service.model.dtos.RideResponse;
import com.safe_ride.rides_service.model.entity.Ride;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface RideMapper {

    @Mapping(target = "pickup.address", source = "pickup")
    @Mapping(target = "pickup.latitude", source = "pickupLat")
    @Mapping(target = "pickup.longitude", source = "pickupLng")
    @Mapping(target = "destination.address", source = "drop")
    @Mapping(target = "destination.latitude", source = "dropLat")
    @Mapping(target = "destination.longitude", source = "dropLng")
    @Mapping(target = "totalSeats", source = "seats")
    @Mapping(target = "availableSeats", source = "seats")
    Ride toRide(CreateRideRequest request);

    @Mapping(target = "passengerId", source = "createdByUserId")
    @Mapping(target = "pickup", source = "pickup.address")
    @Mapping(target = "pickupLat", source = "pickup.latitude")
    @Mapping(target = "pickupLng", source = "pickup.longitude")
    @Mapping(target = "drop", source = "destination.address")
    @Mapping(target = "dropLat", source = "destination.latitude")
    @Mapping(target = "dropLng", source = "destination.longitude")
    @Mapping(target = "seats", source = "totalSeats")
    RideResponse toResponse(Ride ride);
}