package com.saferide.monolith.rides.model.mapper;

import com.saferide.monolith.rides.model.dtos.CreateRideRequest;
import com.saferide.monolith.rides.model.dtos.RideResponse;
import com.saferide.monolith.rides.model.entity.Ride;
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
    // departureTime is set in RideService.createRide (only if it's in the
    // future), so don't let the mapper copy a past/immediate value straight in.
    @Mapping(target = "departureTime", ignore = true)
    Ride toRide(CreateRideRequest request);

    @Mapping(target = "passengerId", source = "createdByUserId")
    @Mapping(target = "pickup", source = "pickup.address")
    @Mapping(target = "pickupLat", source = "pickup.latitude")
    @Mapping(target = "pickupLng", source = "pickup.longitude")
    @Mapping(target = "drop", source = "destination.address")
    @Mapping(target = "dropLat", source = "destination.latitude")
    @Mapping(target = "dropLng", source = "destination.longitude")
    // The host's own party, not the car's capacity. totalSeats is hard-set to
    // MAX_SEATS for every ride, so mapping from it made create/publish/accept
    // responses always claim 4 seats regardless of what the host booked.
    @Mapping(target = "seats", source = "hostSeats")
    RideResponse toResponse(Ride ride);
}