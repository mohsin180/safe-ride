package com.saferide.monolith.rides.service;

import com.saferide.monolith.common.security.UserContext;
import com.saferide.monolith.rides.exceptions.RoleNotAllowedException;
import com.saferide.monolith.rides.model.dtos.DriverStatusResponse;
import com.saferide.monolith.rides.model.entity.DriverStatus;
import com.saferide.monolith.rides.repo.DriverStatusRepository;
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
