package com.saferide.ride_service.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Ride {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false)
    private UUID passengerId;
    @Column(nullable = false)
    private UUID rideId;
    @Column(nullable = false)
    private Double pickupLat;
    @Column(nullable = false)
    private Double pickupLon;
    @Column(nullable = false)
    private Double dropOffLat;
    @Column(nullable = false)
    private Double dropOffLon;
    @Column(nullable = false)
    private Double price;
    @CreatedDate
    private LocalDateTime requestedAt;
    @CreatedDate
    private LocalDateTime acceptedAt;
    @CreatedDate
    private LocalDateTime startedAt;
    @CreatedDate
    private LocalDateTime completedAt;
}
