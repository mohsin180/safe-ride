package com.saferide.profile_service.models.entities;

import com.saferide.profile_service.models.dtos.Role;
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
@Table(name = "passenger_profile")
@EntityListeners(AuditingEntityListener.class)
public class PassengerProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false)
    private String fullName;
    @Column(nullable = false)
    private String cnic;
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Role role;
    @Column(nullable = false)
    private Double rating = 0.0;
    @Column(nullable = false, unique = true)
    private UUID userId;
    @CreatedDate
    private LocalDateTime createdAt;
}
