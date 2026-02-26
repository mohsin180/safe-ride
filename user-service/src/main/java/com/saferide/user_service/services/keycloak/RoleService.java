package com.saferide.user_service.services.keycloak;

import com.saferide.user_service.model.dtos.AssignRoleRequest;
import com.saferide.user_service.model.enums.Role;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.representations.idm.RoleRepresentation;
import org.springframework.stereotype.Service;

import java.util.List;
@Service
public class RoleService {
    private final Keycloak keycloak;
    private final KeycloakProperties properties;

    public RoleService(Keycloak keycloak, KeycloakProperties properties) {
        this.keycloak = keycloak;
        this.properties = properties;
    }

    public void assignRole(String keycloakId, AssignRoleRequest role){
        UserResource userResource = keycloak.realm(properties.getRealm())
                .users()
                .get(keycloakId);
        RoleRepresentation roleRep = keycloak.realm(properties.getRealm())
                .roles()
                .get(role.toString())
                .toRepresentation();
        userResource.roles().realmLevel().add(List.of(roleRep));
    }

}
