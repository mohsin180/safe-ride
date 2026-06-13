package com.safe_ride.rides_service.controller;

import com.safe_ride.rides_service.exceptions.NotFoundException;
import com.safe_ride.rides_service.model.dtos.RideMembersResponse;
import com.safe_ride.rides_service.model.entity.Ride;
import com.safe_ride.rides_service.repo.RideParticipantsRepository;
import com.safe_ride.rides_service.repo.RideRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Service-to-service endpoints — NOT for the mobile client. messaging-service
 * calls these (via RestClient) to resolve a ride's chat members and to list
 * the rides a user has a chat for. Unauthenticated at the security layer
 * (see {@code SecurityConfig}, which permits {@code /api/v1/rides/internal/**});
 * the gateway refuses to route {@code /internal/**} from the public side.
 */
@RestController
@RequestMapping("/api/v1/rides/internal")
public class InternalRidesController {

    private final RideRepository rideRepository;
    private final RideParticipantsRepository rideParticipantsRepository;

    public InternalRidesController(RideRepository rideRepository,
                                   RideParticipantsRepository rideParticipantsRepository) {
        this.rideRepository = rideRepository;
        this.rideParticipantsRepository = rideParticipantsRepository;
    }

    /** Members of one ride's chat: host + driver (if assigned) + co-passengers. */
    @GetMapping("/{rideId}/members")
    @Transactional(readOnly = true)
    public ResponseEntity<RideMembersResponse> getMembers(@PathVariable("rideId") UUID rideId) {
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new NotFoundException("Ride not found"));
        return ResponseEntity.ok(toResponse(ride));
    }

    /** Every chat (ride) a user belongs to — host, driver, or co-passenger. */
    @GetMapping("/users/{userId}/chats")
    @Transactional(readOnly = true)
    public ResponseEntity<List<RideMembersResponse>> getUserChats(
            @PathVariable("userId") UUID userId) {
        List<RideMembersResponse> chats = rideRepository.findChatsForUser(userId)
                .stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(chats);
    }

    private RideMembersResponse toResponse(Ride ride) {
        List<UUID> memberIds = new ArrayList<>();
        memberIds.add(ride.getCreatedByUserId());
        if (ride.getDriverId() != null) {
            memberIds.add(ride.getDriverId());
        }
        memberIds.addAll(rideParticipantsRepository.findUserIdsByRideId(ride.getId()));
        List<UUID> deduped = memberIds.stream().distinct().toList();

        return new RideMembersResponse(
                ride.getId(),
                ride.getPickup() != null ? ride.getPickup().getAddress() : null,
                ride.getDestination() != null ? ride.getDestination().getAddress() : null,
                ride.getStatus() != null ? ride.getStatus().name() : null,
                deduped);
    }
}
