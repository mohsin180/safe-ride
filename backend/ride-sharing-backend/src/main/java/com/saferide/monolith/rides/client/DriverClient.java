package com.saferide.monolith.rides.client;

import com.saferide.monolith.profile.models.entities.DriverProfile;
import com.saferide.monolith.profile.models.entities.Vehicle;
import com.saferide.monolith.profile.repos.DriverProfileRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Former Feign client to profile-service, now a direct in-process facade
 * over the driver profile repository. Same class name + method signatures,
 * so the rides services are unchanged.
 */
@Component
public class DriverClient {

    private final DriverProfileRepository driverProfileRepository;

    public DriverClient(DriverProfileRepository driverProfileRepository) {
        this.driverProfileRepository = driverProfileRepository;
    }

    public List<DriverSummary> getDriverSummaries(List<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        return driverProfileRepository.findByUserIdInFetchVehicle(userIds)
                .stream()
                .map(this::toSummary)
                .toList();
    }

    public void updateDriverRating(UUID userId, RatingUpdateRequest request) {
        DriverProfile d = driverProfileRepository.findByUserId(userId);
        if (d == null) {
            return;
        }
        d.setRating(request.rating());
        driverProfileRepository.save(d);
    }

    private DriverSummary toSummary(DriverProfile d) {
        return new DriverSummary(
                d.getUserId(), d.getFullName(), composeCarInfo(d.getVehicle()),
                d.getRating(), d.getPhoneNo());
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
