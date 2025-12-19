package com.saferide.profile_service.models.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "passenger_profile")
public class PassengerProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false)
    private String fullName;

    private String profilePicture;
    @Column(nullable = false)
    private Double rating = 0.0;
    @Column(nullable = false)
    private UUID userId;
    @CreatedDate
    private LocalDateTime createdAt;
}
