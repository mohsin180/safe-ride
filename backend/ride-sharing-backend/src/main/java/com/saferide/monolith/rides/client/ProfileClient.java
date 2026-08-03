package com.saferide.monolith.rides.client;

import com.saferide.monolith.profile.models.entities.PassengerProfile;
import com.saferide.monolith.profile.repos.PassengerProfileRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Former Feign client to profile-service, now a direct in-process facade
 * over the profile module's repositories. Same class name + method
 * signatures, so {@code RideService}/{@code RatingService} are unchanged.
 */
@Component("ridesProfileClient")
public class ProfileClient {

    private final PassengerProfileRepository passengerProfileRepository;

    public ProfileClient(PassengerProfileRepository passengerProfileRepository) {
        this.passengerProfileRepository = passengerProfileRepository;
    }

    public List<PassengerSummary> getPassengerSummaries(List<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        return passengerProfileRepository.findByUserIdIn(userIds)
                .stream()
                .map(this::toSummary)
                .toList();
    }

    public void updatePassengerRating(UUID userId, RatingUpdateRequest request) {
        PassengerProfile p = passengerProfileRepository.findByUserId(userId);
        if (p == null) {
            return;
        }
        p.setRating(request.rating());
        passengerProfileRepository.save(p);
    }

    private PassengerSummary toSummary(PassengerProfile p) {
        return new PassengerSummary(
                p.getUserId(), p.getFullName(), p.getRating(), p.getPhoneNo());
    }
}
