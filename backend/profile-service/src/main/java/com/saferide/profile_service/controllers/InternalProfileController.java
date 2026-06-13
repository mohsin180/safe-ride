package com.saferide.profile_service.controllers;

import com.saferide.profile_service.models.dtos.DriverSummaryResponse;
import com.saferide.profile_service.models.dtos.PassengerSummaryResponse;
import com.saferide.profile_service.models.dtos.RatingUpdateRequest;
import com.saferide.profile_service.models.entities.DriverProfile;
import com.saferide.profile_service.models.entities.PassengerProfile;
import com.saferide.profile_service.models.entities.Vehicle;
import com.saferide.profile_service.repos.DriverProfileRepository;
import com.saferide.profile_service.repos.PassengerProfileRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Service-to-service endpoints — NOT meant for the mobile client.
 *
 * <p>rides-service calls this (via Feign) to turn the userIds it stores
 * on a ride into real display names for the host / co-passenger cards.
 * It is deliberately unauthenticated at the security layer (see
 * {@code SecurityConfig}, which permits {@code /api/v1/profile/internal/**})
 * because internal callers reach profile-service directly and therefore
 * carry no gateway-injected {@code X-User-*} headers. In a hardened
 * deployment the gateway should refuse to route {@code /internal/**}
 * from the public side.
 */
@RestController
@RequestMapping("/api/v1/profile/internal")
public class InternalProfileController {

    private final PassengerProfileRepository passengerProfileRepository;
    private final DriverProfileRepository driverProfileRepository;

    public InternalProfileController(PassengerProfileRepository passengerProfileRepository,
                                     DriverProfileRepository driverProfileRepository) {
        this.passengerProfileRepository = passengerProfileRepository;
        this.driverProfileRepository = driverProfileRepository;
    }

    /**
     * Batch-resolves a set of passenger userIds into name + rating
     * summaries. Unknown / profile-less ids are simply omitted from the
     * response, so the caller must tolerate a shorter list than it asked
     * for and fall back to a placeholder for the missing ones.
     */
    @PostMapping("/passengers/by-ids")
    public ResponseEntity<List<PassengerSummaryResponse>> getPassengersByIds(
            @RequestBody List<UUID> userIds
    ) {
        if (userIds == null || userIds.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }
        List<PassengerSummaryResponse> summaries = passengerProfileRepository
                .findByUserIdIn(userIds)
                .stream()
                .map(this::toSummary)
                .toList();
        return ResponseEntity.ok(summaries);
    }

    private PassengerSummaryResponse toSummary(PassengerProfile p) {
        return new PassengerSummaryResponse(p.getUserId(), p.getFullName(), p.getRating());
    }

    /**
     * Batch-resolves a set of driver userIds into name + car + rating
     * summaries for the passenger ride-history screen. Like the passenger
     * variant, unknown ids are simply omitted from the response.
     */
    @PostMapping("/drivers/by-ids")
    public ResponseEntity<List<DriverSummaryResponse>> getDriversByIds(
            @RequestBody List<UUID> userIds
    ) {
        if (userIds == null || userIds.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }
        List<DriverSummaryResponse> summaries = driverProfileRepository
                .findByUserIdInFetchVehicle(userIds)
                .stream()
                .map(this::toDriverSummary)
                .toList();
        return ResponseEntity.ok(summaries);
    }

    private DriverSummaryResponse toDriverSummary(DriverProfile d) {
        return new DriverSummaryResponse(
                d.getUserId(), d.getFullName(), composeCarInfo(d.getVehicle()), d.getRating());
    }

    /**
     * Caches a passenger's recomputed average rating (sent by rides-service
     * after a driver rates them). Unknown userId is a no-op 404.
     */
    @PostMapping("/passengers/{userId}/rating")
    public ResponseEntity<Void> updatePassengerRating(
            @PathVariable UUID userId,
            @RequestBody RatingUpdateRequest request
    ) {
        PassengerProfile p = passengerProfileRepository.findByUserId(userId);
        if (p == null) {
            return ResponseEntity.notFound().build();
        }
        p.setRating(request.rating());
        passengerProfileRepository.save(p);
        return ResponseEntity.noContent().build();
    }

    /** Caches a driver's recomputed average rating (sent by rides-service
     *  after a passenger rates them). Unknown userId is a no-op 404. */
    @PostMapping("/drivers/{userId}/rating")
    public ResponseEntity<Void> updateDriverRating(
            @PathVariable UUID userId,
            @RequestBody RatingUpdateRequest request
    ) {
        DriverProfile d = driverProfileRepository.findByUserId(userId);
        if (d == null) {
            return ResponseEntity.notFound().build();
        }
        d.setRating(request.rating());
        driverProfileRepository.save(d);
        return ResponseEntity.noContent().build();
    }

    /** "Toyota Corolla · White" — null when the driver has no vehicle yet. */
    private String composeCarInfo(Vehicle v) {
        if (v == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        if (v.getMake() != null) {
            sb.append(v.getMake());
        }
        if (v.getModel() != null) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(v.getModel());
        }
        if (v.getColor() != null && !v.getColor().isBlank()) {
            if (sb.length() > 0) {
                sb.append(" · ");
            }
            sb.append(v.getColor());
        }
        return sb.length() > 0 ? sb.toString() : null;
    }
}
