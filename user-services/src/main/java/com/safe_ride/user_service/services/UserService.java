package com.safe_ride.user_service.services;

import com.safe_ride.user_service.exceptions.UserAlreadyExistException;
import com.safe_ride.user_service.exceptions.UserNotFoundException;
import com.safe_ride.user_service.model.UserMapper;
import com.safe_ride.user_service.model.Users;
import com.safe_ride.user_service.model.dtos.*;
import com.safe_ride.user_service.repos.UserRepository;
import com.safe_ride.user_service.security.JwtUtil;
import org.apache.coyote.BadRequestException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, UserMapper userMapper, AuthenticationManager authenticationManager, JwtUtil jwtUtil, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
        this.passwordEncoder = passwordEncoder;
    }

    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new UserAlreadyExistException("User already exists");
        }

        Users user = userMapper.toUser(request);
        user.setPassword(passwordEncoder.encode(request.password()));
        userRepository.save(user);
        return userMapper.toResponse(user);
    }

    public LoginResponse login(LoginRequest request) throws BadRequestException {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.email(), request.password())
            );
        } catch (BadCredentialsException e) {
            throw new BadCredentialsException("Invalid email or password");
        }
        Users users = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new UserNotFoundException("User Not Found"));

        if (!users.isEnabled()) {
            throw new BadRequestException("Account is disabled");
        }

        String token = jwtUtil.generateToken(
                users.getId(), users.getRole().name(), users.getGender().name());

        return LoginResponse.builder()
                .token(token)
                .build();
    }

    public LoginResponse selectRole(String id, RoleSelection request) {
        Users users = userRepository.findById(UUID.fromString(id)).orElseThrow(
                () -> new UsernameNotFoundException("User NotFound")
        );
        users.setRole(Role.valueOf(request.role()));
        Users updatedUser = userRepository.save(users);
        String token = jwtUtil.generateToken(
                updatedUser.getId(),
                updatedUser.getRole().name(),
                updatedUser.getGender().name()
        );
        return LoginResponse.builder()
                .token(token)
                .build();
    }
}
