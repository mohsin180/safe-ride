package com.safe_ride.rides_service.service;

import com.safe_ride.rides_service.config.UserContext;
import com.safe_ride.rides_service.exceptions.RoleNotAllowedException;
import com.safe_ride.rides_service.model.dtos.DriverStatusResponse;
import com.safe_ride.rides_service.model.entity.DriverStatus;
import com.safe_ride.rides_service.repo.DriverStatusRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Persists a driver's online/offline availability so the toggle survives
 * app restarts and reflects across devices. One upserted row per driver.
 */
@Service
public class DriverStatusService {

    private final DriverStatusRepository driverStatusRepository;

    public DriverStatusService(DriverStatusRepository driverStatusRepository) {
        this.driverStatusRepository = driverStatusRepository;
    }

    @Transactional
    public DriverStatusResponse setOnline(boolean online) {
        UUID driverId = requireDriverId();
        DriverStatus status = driverStatusRepository.findById(driverId)
                .orElseGet(() -> {
                    DriverStatus s = new DriverStatus();
                    s.setDriverId(driverId);
                    return s;
                });
        status.setOnline(online);
        status.setLastSeenAt(Instant.now());
        DriverStatus saved = driverStatusRepository.save(status);
        return new DriverStatusResponse(saved.isOnline(), saved.getLastSeenAt());
    }

    @Transactional(readOnly = true)
    public DriverStatusResponse getStatus() {
        UUID driverId = requireDriverId();
        // A driver who never toggled is treated as offline.
        return driverStatusRepository.findById(driverId)
                .map(s -> new DriverStatusResponse(s.isOnline(), s.getLastSeenAt()))
                .orElse(new DriverStatusResponse(false, null));
    }

    private UUID requireDriverId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assert authentication != null;
        UserContext ctx = (UserContext) authentication.getDetails();
        if (!"DRIVER".equals(ctx.role())) {
            throw new RoleNotAllowedException("Only drivers have an availability status.");
        }
        return ctx.userId();
    }
}
