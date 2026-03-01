package com.safe_ride.user_service.services;

import com.safe_ride.user_service.exceptions.UserAlreadyExistException;
import com.safe_ride.user_service.exceptions.UserNotFoundException;
import com.safe_ride.user_service.model.UserMapper;
import com.safe_ride.user_service.model.Users;
import com.safe_ride.user_service.model.dtos.LoginRequest;
import com.safe_ride.user_service.model.dtos.LoginResponse;
import com.safe_ride.user_service.model.dtos.RegisterRequest;
import com.safe_ride.user_service.model.dtos.UserResponse;
import com.safe_ride.user_service.repos.UserRepository;
import com.safe_ride.user_service.security.JwtUtil;
import org.apache.coyote.BadRequestException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;

    public UserService(UserRepository userRepository, UserMapper userMapper, AuthenticationManager authenticationManager, JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
    }

    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new UserAlreadyExistException("User already exists");
        }
        Users user = userMapper.toUser(request);
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
                .role(users.getRole().name())
                .gender(users.getGender().name())
                .email(users.getEmail())
                .build();
    }
}
