package com.safe_ride.rides_service.controller;

import com.safe_ride.rides_service.model.dtos.AvailableRideResponse;
import com.safe_ride.rides_service.model.dtos.CreateRideRequest;
import com.safe_ride.rides_service.model.dtos.RideDetailsResponse;
import com.safe_ride.rides_service.model.dtos.RideResponse;
import com.safe_ride.rides_service.model.dtos.RideStatsResponse;
import com.safe_ride.rides_service.service.RideService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/rides")
public class RideController {
    private final RideService rideService;

    public RideController(RideService rideService) {
        this.rideService = rideService;
    }

    @PostMapping
    @PreAuthorize("hasRole('PASSENGER')")
    public ResponseEntity<RideResponse> createRide(@Valid @RequestBody CreateRideRequest request) {
        RideResponse response = rideService.createRide(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/available")
    @PreAuthorize("hasRole('PASSENGER')")
    public ResponseEntity<List<AvailableRideResponse>> getAvailableRides(
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng
    ) {
        return ResponseEntity.ok(rideService.getAvailableRides(lat, lng));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('PASSENGER')")
    public ResponseEntity<Void> cancelRide(@PathVariable("id") UUID id) {
        rideService.cancelRide(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/join")
    @PreAuthorize("hasRole('PASSENGER')")
    public ResponseEntity<Void> joinRide(@PathVariable("id") UUID id) {
        rideService.joinRide(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/leave")
    @PreAuthorize("hasRole('PASSENGER')")
    public ResponseEntity<Void> leaveRide(@PathVariable("id") UUID id) {
        rideService.leaveRide(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('PASSENGER')")
    public ResponseEntity<List<AvailableRideResponse>> getMyRides() {
        return ResponseEntity.ok(rideService.getMyRides());
    }

    @GetMapping("/stats")
    public ResponseEntity<RideStatsResponse> getMyStats() {
        RideStatsResponse response = rideService.getMyStats();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('PASSENGER')")
    public ResponseEntity<RideDetailsResponse> getRideDetails(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(rideService.getRideDetails(id));
    }
}