package com.saferide.profile_service.models.dtos;

import java.util.UUID;

public record PassengerProfileResponse(
        UUID id,
        String fullName,
        String cnic,
        String phoneNo,
        Double rating
) {
}
