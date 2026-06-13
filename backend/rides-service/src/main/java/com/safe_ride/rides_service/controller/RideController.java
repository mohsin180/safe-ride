package com.safe_ride.rides_service.controller;

import com.safe_ride.rides_service.model.dtos.AvailableRideResponse;
import com.safe_ride.rides_service.model.dtos.CreateRideRequest;
import com.safe_ride.rides_service.model.dtos.JoinRequestBody;
import com.safe_ride.rides_service.model.dtos.PassengerRideHistoryResponse;
import com.safe_ride.rides_service.model.dtos.RateDriverRequest;
import com.safe_ride.rides_service.model.dtos.RatePassengerRequest;
import com.safe_ride.rides_service.model.dtos.RideDetailsResponse;
import com.safe_ride.rides_service.model.dtos.RideResponse;
import com.safe_ride.rides_service.model.dtos.RideStatsResponse;
import com.safe_ride.rides_service.service.RatingService;
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
    private final RatingService ratingService;

    public RideController(RideService rideService, RatingService ratingService) {
        this.rideService = rideService;
        this.ratingService = ratingService;
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

    /** Request to join — the host must accept before the rider is added. */
    @PostMapping("/{id}/join")
    @PreAuthorize("hasRole('PASSENGER')")
    public ResponseEntity<Void> requestToJoin(
            @PathVariable("id") UUID id,
            @RequestBody(required = false) JoinRequestBody body) {
        rideService.requestToJoin(id, body);
        return ResponseEntity.noContent().build();
    }

    /** Host accepts a pending join request (the requester then joins). */
    @PostMapping("/{id}/join-requests/{requestId}/accept")
    @PreAuthorize("hasRole('PASSENGER')")
    public ResponseEntity<Void> acceptJoinRequest(
            @PathVariable("id") UUID id,
            @PathVariable("requestId") UUID requestId) {
        rideService.acceptJoinRequest(id, requestId);
        return ResponseEntity.noContent().build();
    }

    /** Host declines a pending join request. */
    @PostMapping("/{id}/join-requests/{requestId}/decline")
    @PreAuthorize("hasRole('PASSENGER')")
    public ResponseEntity<Void> declineJoinRequest(
            @PathVariable("id") UUID id,
            @PathVariable("requestId") UUID requestId) {
        rideService.declineJoinRequest(id, requestId);
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

    @GetMapping("/history")
    @PreAuthorize("hasRole('PASSENGER')")
    public ResponseEntity<List<PassengerRideHistoryResponse>> getMyHistory() {
        return ResponseEntity.ok(rideService.getMyHistory());
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

    /** The passenger's in-progress trip (ACCEPTED/STARTED) for the live map. */
    @GetMapping("/my/active")
    @PreAuthorize("hasRole('PASSENGER')")
    public ResponseEntity<List<RideDetailsResponse>> getMyActiveTrip() {
        return ResponseEntity.ok(rideService.getMyActiveTrip());
    }

    // ── Driver ride-lifecycle ──────────────────────────────────────

    @GetMapping("/driver/feed")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<List<AvailableRideResponse>> getDriverFeed() {
        return ResponseEntity.ok(rideService.getDriverFeed());
    }

    @PostMapping("/{id}/accept")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<RideResponse> acceptRide(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(rideService.acceptRide(id));
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<RideResponse> startRide(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(rideService.startRide(id));
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<RideResponse> completeRide(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(rideService.completeRide(id));
    }

    /** The assigned driver backs out — ride re-opens (PENDING) for another driver. */
    @PostMapping("/{id}/driver-cancel")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<Void> driverCancelRide(@PathVariable("id") UUID id) {
        rideService.driverCancelRide(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/driver/active")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<List<RideDetailsResponse>> getDriverActiveRides() {
        return ResponseEntity.ok(rideService.getDriverActiveRides());
    }

    // ── Ratings (post-trip, on a COMPLETED ride) ──────────────────

    @PostMapping("/{id}/rate/driver")
    @PreAuthorize("hasRole('PASSENGER')")
    public ResponseEntity<Void> rateDriver(@PathVariable("id") UUID id,
                                           @Valid @RequestBody RateDriverRequest request) {
        ratingService.rateDriver(id, request.stars());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/rate/passenger")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<Void> ratePassenger(@PathVariable("id") UUID id,
                                              @Valid @RequestBody RatePassengerRequest request) {
        ratingService.ratePassenger(id, request.ratedId(), request.stars());
        return ResponseEntity.noContent().build();
    }

    /** A passenger rates a co-passenger (leave-time, bidirectional). */
    @PostMapping("/{id}/rate/copassenger")
    @PreAuthorize("hasRole('PASSENGER')")
    public ResponseEntity<Void> rateCoPassenger(@PathVariable("id") UUID id,
                                                @Valid @RequestBody RatePassengerRequest request) {
        ratingService.rateCoPassenger(id, request.ratedId(), request.stars());
        return ResponseEntity.noContent().build();
    }
}