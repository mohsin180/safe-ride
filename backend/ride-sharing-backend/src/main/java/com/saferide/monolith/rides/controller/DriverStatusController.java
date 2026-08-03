package com.saferide.monolith.rides.controller;

import com.saferide.monolith.rides.model.dtos.DriverStatusResponse;
import com.saferide.monolith.rides.service.DriverStatusService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Driver availability toggle. Lives under the rides-service so it shares
 * the gateway route ({@code /api/v1/rides/**}) and the same X-User-* auth.
 */
@RestController
@RequestMapping("/api/v1/rides/driver")
public class DriverStatusController {

    private final DriverStatusService driverStatusService;

    public DriverStatusController(DriverStatusService driverStatusService) {
        this.driverStatusService = driverStatusService;
    }

    @GetMapping("/status")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<DriverStatusResponse> getStatus() {
        return ResponseEntity.ok(driverStatusService.getStatus());
    }

    @PostMapping("/online")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<DriverStatusResponse> goOnline() {
        return ResponseEntity.ok(driverStatusService.setOnline(true));
    }

    @PostMapping("/offline")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<DriverStatusResponse> goOffline() {
        return ResponseEntity.ok(driverStatusService.setOnline(false));
    }
}
