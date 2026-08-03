package com.saferide.monolith.messaging.client;

import com.saferide.monolith.profile.repos.DriverProfileRepository;
import com.saferide.monolith.profile.repos.PassengerProfileRepository;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Former RestClient to profile-service's internal batch endpoints, now a
 * direct in-process facade over the profile repositories. A chat member is
 * either a passenger or a driver, so both tables are queried and merged.
 */
@Component("messagingProfileClient")
public class ProfileClient {

    private final PassengerProfileRepository passengerProfileRepository;
    private final DriverProfileRepository driverProfileRepository;

    public ProfileClient(PassengerProfileRepository passengerProfileRepository,
                         DriverProfileRepository driverProfileRepository) {
        this.passengerProfileRepository = passengerProfileRepository;
        this.driverProfileRepository = driverProfileRepository;
    }

    public Map<UUID, String> resolveNames(Collection<UUID> userIds) {
        Map<UUID, String> names = new HashMap<>();
        if (userIds == null || userIds.isEmpty()) {
            return names;
        }
        List<UUID> ids = userIds.stream().filter(Objects::nonNull).distinct().toList();
        passengerProfileRepository.findByUserIdIn(ids).forEach(p ->
                putName(names, p.getUserId(), p.getFullName()));
        driverProfileRepository.findByUserIdInFetchVehicle(ids).forEach(d ->
                putName(names, d.getUserId(), d.getFullName()));
        return names;
    }

    private void putName(Map<UUID, String> names, UUID userId, String fullName) {
        if (userId != null && fullName != null && !fullName.isBlank()) {
            names.putIfAbsent(userId, fullName);
        }
    }
}
