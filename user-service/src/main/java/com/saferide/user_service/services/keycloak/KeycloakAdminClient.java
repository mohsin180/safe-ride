package com.saferide.user_service.services.keycloak;

import com.saferide.user_service.exceptions.LoginError;
import com.saferide.user_service.exceptions.RegistrationError;
import com.saferide.user_service.model.dtos.LoginRequest;
import com.saferide.user_service.model.dtos.LoginResponse;
import com.saferide.user_service.model.dtos.RegisterRequest;
import jakarta.ws.rs.core.Response;
import org.jspecify.annotations.Nullable;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
public class KeycloakAdminClient {
    private final Keycloak keycloak;
    private final KeycloakProperties properties;

    public KeycloakAdminClient(Keycloak keycloak, KeycloakProperties properties) {
        this.keycloak = keycloak;
        this.properties = properties;
    }

    private static UserRepresentation getUserRepresentation(RegisterRequest request) {
        UserRepresentation userRepresentation = new UserRepresentation();
        userRepresentation.setUsername(request.username());
        userRepresentation.setEmail(request.email());
        userRepresentation.setEnabled(true);
        userRepresentation.setEmailVerified(false);

        CredentialRepresentation passwordCred = new CredentialRepresentation();
        passwordCred.setTemporary(false);
        passwordCred.setType(CredentialRepresentation.PASSWORD);
        passwordCred.setValue(request.password());

        userRepresentation.setCredentials(List.of(passwordCred));

        // Ensure NO required actions exist
        userRepresentation.setRequiredActions(Collections.emptyList());
        return userRepresentation;
    }

    public String createUserInKeycloak(RegisterRequest request) {
        UserRepresentation userRepresentation = getUserRepresentation(request);

        Response response = keycloak
                .realm(properties.getRealm())
                .users()
                .create(userRepresentation);

        try {
            if (response.getStatus() != 201) {
                String error = response.readEntity(String.class);
                throw new RegistrationError("Failed to create user: " + error, response.getStatus());
            }
        } finally {
            response.close();
        }

        UsersResource usersResource = keycloak.realm(properties.getRealm()).users();
        List<UserRepresentation> users =
                usersResource.searchByUsername(userRepresentation.getUsername(), true);

        UserRepresentation createdUser = users.stream()
                .findFirst()
                .orElseThrow(() -> new RuntimeException("User not found in Keycloak after creation"));

        verifyEmail(createdUser.getId());

        return createdUser.getId();
    }

    public Boolean isEmailVerified(String userId) {
        UserRepresentation user = keycloak.realm(properties.getRealm())
                .users()
                .get(userId)
                .toRepresentation();
        return Boolean.TRUE.equals(user.isEmailVerified());
    }


    public void verifyEmail(String userId) {
        UsersResource usersResource = keycloak.realm(properties.getRealm()).users();
        usersResource.get(userId).sendVerifyEmail();
    }

    public LoginResponse loginUser(LoginRequest request) {
        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "password");
        formData.add("client_id", properties.getClientId());
        formData.add("client_secret", properties.getClientSecret());
        formData.add("username", request.username());
        formData.add("password", request.password());

        HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<>(formData, headers);

        String tokenUrl = properties.getServerUrl() + "/realms/" + properties.getRealm() +
                "/protocol/openid-connect/token";

        ResponseEntity<Map> response = restTemplate.postForEntity(tokenUrl, requestEntity, Map.class);
        if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
            @Nullable Map responseBody = response.getBody();
            return new LoginResponse(
                    (String) responseBody.get("access_token"),
                    (String) responseBody.get("refresh_token"),
                    (String) responseBody.get("token_type"),
                    (Integer) responseBody.get("expires_in")
            );
        } else {
            throw new LoginError("Failed to Login in Keycloak: " + response.getStatusCode(), response.getStatusCode());
        }
    }
}
