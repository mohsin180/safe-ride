package com.saferide.user_service;

import com.saferide.user_service.model.dtos.RegisterRequest;
import com.saferide.user_service.model.dtos.RegisterResponse;
import com.saferide.user_service.model.entities.Users;
import com.saferide.user_service.model.enums.Gender;
import com.saferide.user_service.model.enums.Role;
import com.saferide.user_service.model.mappers.UserMapper;
import com.saferide.user_service.repos.UserRepository;
import com.saferide.user_service.services.UserService;
import com.saferide.user_service.services.keycloak.KeycloakAdminClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UserServiceTest {
    @Mock
    private KeycloakAdminClient keycloakAdminClient;
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserMapper userMapper;
    @InjectMocks
    private UserService userService;

    @Test
    void shouldCreateUserSuccessfully() {
        RegisterRequest request = new RegisterRequest(
                "mohsin",
                "mughal@gmail.com",
                "kgfpart@2",
                "03249503132",
                Gender.MALE
        );
        Users users = new Users();
        users.setId(UUID.randomUUID());
        users.setUsername("mohsin");
        users.setEmail("mohsin@gmail.com");
        when(keycloakAdminClient.createUserInKeycloak(request)).thenReturn(UUID.randomUUID().toString());
        when(userMapper.toUser(request)).thenReturn(users);
        when(userRepository.save(any(Users.class))).thenReturn(users);
        when(userMapper.toResponse(users)).thenReturn(
                new RegisterResponse(users.getId(), users.getUsername(), users.getEmail(), users.getRole(), users.getGender())
        );

        RegisterResponse response = userService.createUser(request);
        assertNotNull(response);
        assertEquals("mohsin", response.username());
        verify(keycloakAdminClient).createUserInKeycloak(request);
        verify(userRepository).save(any(Users.class));
    }


}
