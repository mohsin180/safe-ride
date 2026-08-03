package com.saferide.monolith.rides.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RideParticipants {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ride_id", nullable = false)
    private Ride ride;
    @Column(nullable = false)
    private UUID userId;
    /** How many seats this co-passenger reserved. Freed back to the ride when
     *  they leave. {@code @ColumnDefault} so the column backfills existing
     *  rows (each historical join was a single seat). */
    @Column(nullable = false)
    @ColumnDefault("1")
    private int seats = 1;
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime joinedAt;
}
