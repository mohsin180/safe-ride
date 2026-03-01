package com.safe_ride.user_service.controller;

import com.safe_ride.user_service.model.dtos.LoginRequest;
import com.safe_ride.user_service.model.dtos.LoginResponse;
import com.safe_ride.user_service.model.dtos.RegisterRequest;
import com.safe_ride.user_service.model.dtos.UserResponse;
import com.safe_ride.user_service.services.UserService;
import org.apache.coyote.BadRequestException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@RequestBody RegisterRequest request) {
        UserResponse register = userService.register(request);
        return ResponseEntity.ok(register);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) throws BadRequestException {
        LoginResponse login = userService.login(request);
        return ResponseEntity.ok(login);
    }
}
