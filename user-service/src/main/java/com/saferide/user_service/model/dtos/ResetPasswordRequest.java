package com.saferide.user_service.model.dtos;

public record ResetPasswordRequest(
        String token,
        String newPassword
) {
}
