package com.saferide.profile_service.models.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "driver_profile")
@EntityListeners(AuditingEntityListener.class)
public class DriverProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private UUID userId;

    @Column(nullable = false)
    private String fullName;

    @Column(nullable = false)
    private String vehicleModel;

    @Column(nullable = false)
    private String vehicleNumber;
    @Column(nullable = false)
    private String licenseNumber;

    private Double driverRating = 0.0;
    @CreatedDate
    @Column(nullable = false)
    private LocalDateTime createdAt;
}
