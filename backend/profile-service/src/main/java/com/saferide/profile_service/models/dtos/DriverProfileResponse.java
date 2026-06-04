package com.saferide.profile_service.models.dtos;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;
@Data
public class DriverProfileResponse{
    private UUID id;
    private String fullName;
    private  String cnic;
    private  String phoneNo;
    private   String email;
    private  String gender;
    private LocalDateTime createdAt;
    private VehicleResponse vehicle;
}
