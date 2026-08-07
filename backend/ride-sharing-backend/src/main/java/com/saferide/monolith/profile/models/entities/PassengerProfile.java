package com.saferide.monolith.profile.models.entities;

import com.saferide.monolith.kyc.model.KycStatus;
import com.saferide.monolith.kyc.model.KycVerifiable;
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
public class PassengerProfile implements KycVerifiable {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false)
    private String fullName;
    @Column(nullable = false)
    private String cnic;
    @Column(nullable = false)
    private String phoneNo;
    @Column(nullable = false)
    private Double rating = 0.0;
    @Column(nullable = false, unique = true)
    private UUID userId;
    @CreatedDate
    private LocalDateTime createdAt;

    // Didit KYC — columns are nullable because ddl-auto=update can't add a
    // NOT NULL column to existing rows; null kycStatus reads as NOT_STARTED.
    @Enumerated(EnumType.STRING)
    private KycStatus kycStatus = KycStatus.NOT_STARTED;
    private String kycSessionId;
    private LocalDateTime kycVerifiedAt;
    @Column(length = 500)
    private String kycRejectionReason;
}
