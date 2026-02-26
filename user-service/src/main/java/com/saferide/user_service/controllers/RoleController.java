package com.saferide.user_service.controllers;

import com.saferide.user_service.model.dtos.AssignRoleRequest;
import com.saferide.user_service.model.enums.Role;
import com.saferide.user_service.services.keycloak.RoleService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/select-role")
public class RoleController {
    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }
    @PutMapping("/{userId}")
    public ResponseEntity<Void> assignRole(@PathVariable String userId,
                                        @RequestBody AssignRoleRequest role){
        roleService.assignRole(userId,role);
        return ResponseEntity.ok().build();
    }
}
