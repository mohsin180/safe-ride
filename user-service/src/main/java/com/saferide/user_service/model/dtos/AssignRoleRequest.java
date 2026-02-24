package com.saferide.user_service.model.dtos;

import com.saferide.user_service.model.enums.Role;

public record AssignRoleRequest(
        Role role
) {
}
