package com.saferide.monolith.rides.controller;

import com.saferide.monolith.rides.model.dtos.AvailableRideResponse;
import com.saferide.monolith.rides.model.dtos.CancellationResult;
import com.saferide.monolith.rides.model.dtos.CancelRideRequest;
import com.saferide.monolith.rides.model.dtos.CreateRideRequest;
import com.saferide.monolith.rides.model.dtos.DriverEarningsResponse;
import com.saferide.monolith.rides.model.dtos.DriverRideHistoryResponse;
import com.saferide.monolith.rides.model.dtos.HostAcceptFarePreviewResponse;
import com.saferide.monolith.rides.model.dtos.JoinFarePreviewResponse;
import com.saferide.monolith.rides.model.dtos.JoinRequestBody;
import com.saferide.monolith.rides.model.dtos.PassengerRideHistoryResponse;
import com.saferide.monolith.rides.model.dtos.PaymentResponse;
import com.saferide.monolith.rides.model.dtos.RateDriverRequest;
import com.saferide.monolith.rides.model.dtos.ReportUserRequest;
import com.saferide.monolith.rides.model.dtos.RatePassengerRequest;
import com.saferide.monolith.rides.model.dtos.RideDetailsResponse;
import com.saferide.monolith.rides.model.dtos.RideResponse;
import com.saferide.monolith.rides.model.dtos.RideStatsResponse;
import com.saferide.monolith.rides.service.RatingService;
import com.saferide.monolith.rides.service.RideService;
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
            @RequestParam(required = false) Double lng,
            @RequestParam(required = false) Double dropLat,
            @RequestParam(required = false) Double dropLng,
            @RequestParam(required = false) Integer seats
    ) {
        return ResponseEntity.ok(
                rideService.getAvailableRides(lat, lng, dropLat, dropLng, seats));
    }

    /** TRUE fare preview for joining with a given route + seats. */
    @GetMapping("/{id}/fare-preview")
    @PreAuthorize("hasRole('PASSENGER')")
    public ResponseEntity<JoinFarePreviewResponse> farePreview(
            @PathVariable("id") UUID id,
            @RequestParam double pickupLat, @RequestParam double pickupLng,
            @RequestParam double dropLat, @RequestParam double dropLng,
            @RequestParam(defaultValue = "1") int seats) {
        return ResponseEntity.ok(rideService.previewJoinFare(
                id, pickupLat, pickupLng, dropLat, dropLng, seats));
    }

    /** The host's before/after fare picture for a pending join request. */
    @GetMapping("/{id}/join-requests/{requestId}/fare-preview")
    @PreAuthorize("hasRole('PASSENGER')")
    public ResponseEntity<HostAcceptFarePreviewResponse> acceptFarePreview(
            @PathVariable("id") UUID id,
            @PathVariable("requestId") UUID requestId) {
        return ResponseEntity.ok(rideService.previewAcceptFare(id, requestId));
    }

    /** Host cancels their ride. Free before the driver arrives; a flat fee is
     *  recorded if cancelled after (ARRIVED). Body (with an optional reason) is
     *  optional. Returns the fee applied + the rider's strike count. */
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('PASSENGER')")
    public ResponseEntity<CancellationResult> cancelRide(
            @PathVariable("id") UUID id,
            @RequestBody(required = false) CancelRideRequest body) {
        String reason = body != null ? body.reason() : null;
        return ResponseEntity.ok(rideService.cancelRide(id, reason));
    }

    /** Host publishes the ride to the driver feed (works even with riders
     *  already joined / seats full). */
    @PostMapping("/{id}/publish")
    @PreAuthorize("hasRole('PASSENGER')")
    public ResponseEntity<RideResponse> publishRide(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(rideService.publishRide(id));
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
    @PreAuthorize("hasAnyRole('PASSENGER','DRIVER')")
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
    public ResponseEntity<List<AvailableRideResponse>> getDriverFeed(
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng
    ) {
        return ResponseEntity.ok(rideService.getDriverFeed(lat, lng));
    }

    /** A driver offers to drive — the host must accept before they're assigned. */
    @PostMapping("/{id}/driver-offer")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<Void> offerToDrive(@PathVariable("id") UUID id) {
        rideService.offerToDrive(id);
        return ResponseEntity.noContent().build();
    }

    /** The driver dismisses a ride from their feed — it stays hidden for them. */
    @PostMapping("/{id}/driver-decline")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<Void> declineRideFromFeed(@PathVariable("id") UUID id) {
        rideService.declineRideFromFeed(id);
        return ResponseEntity.noContent().build();
    }

    /** Report another user for misconduct on a ride (recorded for moderation). */
    @PostMapping("/{id}/report")
    @PreAuthorize("hasAnyRole('PASSENGER','DRIVER')")
    public ResponseEntity<Void> reportUser(
            @PathVariable("id") UUID id, @RequestBody ReportUserRequest body) {
        rideService.reportUser(id, body.reportedUserId(), body.reason());
        return ResponseEntity.noContent().build();
    }

    /** Block another user — they no longer appear in each other's matching. */
    @PostMapping("/users/{userId}/block")
    @PreAuthorize("hasAnyRole('PASSENGER','DRIVER')")
    public ResponseEntity<Void> blockUser(@PathVariable("userId") UUID userId) {
        rideService.blockUser(userId);
        return ResponseEntity.noContent().build();
    }

    /** Host accepts a driver's offer (the driver is assigned, ride → ACCEPTED). */
    @PostMapping("/{id}/driver-offers/{offerId}/accept")
    @PreAuthorize("hasRole('PASSENGER')")
    public ResponseEntity<RideResponse> acceptDriverOffer(
            @PathVariable("id") UUID id,
            @PathVariable("offerId") UUID offerId) {
        return ResponseEntity.ok(rideService.acceptDriverOffer(id, offerId));
    }

    /** Host declines a driver's offer. */
    @PostMapping("/{id}/driver-offers/{offerId}/decline")
    @PreAuthorize("hasRole('PASSENGER')")
    public ResponseEntity<Void> declineDriverOffer(
            @PathVariable("id") UUID id,
            @PathVariable("offerId") UUID offerId) {
        rideService.declineDriverOffer(id, offerId);
        return ResponseEntity.noContent().build();
    }

    /** The assigned driver has reached the pickup point (ACCEPTED → ARRIVED). */
    @PostMapping("/{id}/arrive")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<RideResponse> arriveRide(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(rideService.arriveRide(id));
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

    /** The assigned driver marks a rider as picked up (in the vehicle). */
    @PostMapping("/{id}/riders/{riderId}/pickup")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<Void> markRiderPickedUp(
            @PathVariable("id") UUID id, @PathVariable("riderId") UUID riderId) {
        rideService.markRiderPickedUp(id, riderId);
        return ResponseEntity.noContent().build();
    }

    /** The assigned driver drops a rider at their stop (settles their cash). */
    @PostMapping("/{id}/riders/{riderId}/dropoff")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<Void> markRiderDroppedOff(
            @PathVariable("id") UUID id, @PathVariable("riderId") UUID riderId) {
        rideService.markRiderDroppedOff(id, riderId);
        return ResponseEntity.noContent().build();
    }

    /** The ride's cash fare ledger (one entry per rider). Any member may read
     *  it — the driver to collect, riders to see their own paid state. */
    @GetMapping("/{id}/payments")
    @PreAuthorize("hasAnyRole('PASSENGER','DRIVER')")
    public ResponseEntity<List<PaymentResponse>> getPayments(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(rideService.getRidePayments(id));
    }

    /** The assigned driver confirms they collected a rider's cash fare. */
    @PostMapping("/{id}/payments/{riderId}/collect")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<PaymentResponse> collectPayment(
            @PathVariable("id") UUID id,
            @PathVariable("riderId") UUID riderId) {
        return ResponseEntity.ok(rideService.collectPayment(id, riderId));
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

    /** The driver's completed + cancelled rides for the ride-history screen. */
    @GetMapping("/driver/history")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<List<DriverRideHistoryResponse>> getDriverHistory() {
        return ResponseEntity.ok(rideService.getDriverHistory());
    }

    /** The driver's earnings summary — today's take plus lifetime totals. */
    @GetMapping("/driver/earnings")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<DriverEarningsResponse> getDriverEarnings() {
        return ResponseEntity.ok(rideService.getDriverEarnings());
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