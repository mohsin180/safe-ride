package com.safe_ride.rides_service.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Persisted online/offline availability for a driver. The driver's userId
 * is the natural primary key — one row per driver, upserted on each toggle.
 * {@code lastSeenAt} records when the flag last changed so a future
 * heartbeat/staleness check has something to work with.
 */
@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "driver_status")
public class DriverStatus {

    @Id
    @Column(name = "driver_id")
    private UUID driverId;

    @Column(nullable = false)
    private boolean online;

    @Column(nullable = false)
    private Instant lastSeenAt;
}
