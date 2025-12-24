package com.saferide.ride_service.controller;

import com.saferide.ride_service.model.dtos.RideRequest;
import com.saferide.ride_service.model.dtos.RideResponse;
import com.saferide.ride_service.model.dtos.Role;
import com.saferide.ride_service.services.RideService;
import jakarta.ws.rs.BadRequestException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ride")
public class RideController {
    private final RideService rideService;

    public RideController(RideService rideService) {
        this.rideService = rideService;
    }

    public ResponseEntity<RideResponse> createRide(@RequestBody RideRequest request,
                                                   @RequestHeader("X-User-Id") String userId,
                                                   @RequestHeader("X-User-Role") Role role) {
        if (role != Role.PASSENGER) {
            throw new BadRequestException("Invalid role");
        }
        RideResponse response = rideService.createRide(request, userId);
        return ResponseEntity.ok(response);
    }
}
