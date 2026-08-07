package com.saferide.monolith.profile.models.dtos;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class PassengerProfileResponse {
    private UUID id;
    private String fullName;
    private String cnic;
    private String phoneNo;
    private String email;
    private String gender;
    private LocalDateTime createdAt;
    /** Didit KYC state (NOT_STARTED / IN_PROGRESS / IN_REVIEW / APPROVED / DECLINED). */
    private String kycStatus;
}