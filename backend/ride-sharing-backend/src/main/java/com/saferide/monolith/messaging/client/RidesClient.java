package com.saferide.monolith.messaging.client;

import com.saferide.monolith.rides.model.entity.Ride;
import com.saferide.monolith.rides.repo.RideParticipantsRepository;
import com.saferide.monolith.rides.repo.RideRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Former RestClient to rides-service's internal membership endpoints, now a
 * direct in-process facade over the rides module's repositories. rides still
 * owns the truth of who belongs to a ride; messaging just reads it to
 * authorize chat access and build the chat list.
 */
@Component
public class RidesClient {

    private final RideRepository rideRepository;
    private final RideParticipantsRepository rideParticipantsRepository;

    public RidesClient(RideRepository rideRepository,
                       RideParticipantsRepository rideParticipantsRepository) {
        this.rideRepository = rideRepository;
        this.rideParticipantsRepository = rideParticipantsRepository;
    }

    /** Member userIds + route for one ride, or null if not found. */
    @Transactional(readOnly = true)
    public RideMembers getMembers(UUID rideId) {
        return rideRepository.findById(rideId)
                .map(this::toMembers)
                .orElse(null);
    }

    /** Every ride (chat) a user belongs to. */
    @Transactional(readOnly = true)
    public List<RideMembers> getUserChats(UUID userId) {
        return rideRepository.findChatsForUser(userId)
                .stream()
                .map(this::toMembers)
                .toList();
    }

    /** True if the user is a member of the ride's chat. */
    @Transactional(readOnly = true)
    public boolean isMember(UUID rideId, UUID userId) {
        RideMembers members = getMembers(rideId);
        return members != null && members.memberIds() != null
                && members.memberIds().contains(userId);
    }

    /**
     * True only for the driver actually assigned to this ride. Live-tracking
     * writes are keyed on the ride, so any member who claimed
     * {@code role: "DRIVER"} could overwrite the real car's position and show
     * every rider a vehicle that isn't there.
     */
    @Transactional(readOnly = true)
    public boolean isAssignedDriver(UUID rideId, UUID userId) {
        return rideRepository.findById(rideId)
                .map(r -> r.getDriverId() != null && r.getDriverId().equals(userId))
                .orElse(false);
    }

    private RideMembers toMembers(Ride ride) {
        List<UUID> memberIds = new ArrayList<>();
        memberIds.add(ride.getCreatedByUserId());
        if (ride.getDriverId() != null) {
            memberIds.add(ride.getDriverId());
        }
        memberIds.addAll(rideParticipantsRepository.findUserIdsByRideId(ride.getId()));
        List<UUID> deduped = memberIds.stream().distinct().toList();

        return new RideMembers(
                ride.getId(),
                ride.getPickup() != null ? ride.getPickup().getAddress() : null,
                ride.getDestination() != null ? ride.getDestination().getAddress() : null,
                ride.getStatus() != null ? ride.getStatus().name() : null,
                deduped);
    }
}
