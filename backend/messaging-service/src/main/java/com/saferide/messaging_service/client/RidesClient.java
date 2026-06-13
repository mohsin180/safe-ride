package com.saferide.messaging_service.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.UUID;

/**
 * Calls rides-service's internal membership endpoints. rides-service owns
 * the truth of who belongs to a ride; messaging-service just reads it to
 * authorize chat access and build the chat list.
 */
@Component
public class RidesClient {

    private static final Logger log = LoggerFactory.getLogger(RidesClient.class);

    private final RestClient client;

    public RidesClient(@Value("${rides.service.url:http://localhost:8003}") String baseUrl) {
        this.client = RestClient.create(baseUrl);
    }

    /** Member userIds + route for one ride, or null if unreachable / not found. */
    public RideMembers getMembers(UUID rideId) {
        try {
            return client.get()
                    .uri("/api/v1/rides/internal/{rideId}/members", rideId)
                    .retrieve()
                    .body(RideMembers.class);
        } catch (Exception e) {
            log.warn("getMembers({}) failed: {}", rideId, e.getMessage());
            return null;
        }
    }

    /** Every ride (chat) a user belongs to. Empty on failure. */
    public List<RideMembers> getUserChats(UUID userId) {
        try {
            List<RideMembers> chats = client.get()
                    .uri("/api/v1/rides/internal/users/{userId}/chats", userId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<RideMembers>>() {});
            return chats != null ? chats : List.of();
        } catch (Exception e) {
            log.warn("getUserChats({}) failed: {}", userId, e.getMessage());
            return List.of();
        }
    }

    /** True if the user is a member of the ride's chat. */
    public boolean isMember(UUID rideId, UUID userId) {
        RideMembers members = getMembers(rideId);
        return members != null && members.memberIds() != null
                && members.memberIds().contains(userId);
    }
}
