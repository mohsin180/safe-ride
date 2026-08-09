package com.saferide.monolith.user.services;

import com.saferide.monolith.user.exceptions.InvalidOnboardingTokenException;
import com.saferide.monolith.user.exceptions.UserAlreadyExistException;
import com.saferide.monolith.user.exceptions.GenderLockedException;
import com.saferide.monolith.user.exceptions.UserNotFoundException;
import io.jsonwebtoken.JwtException;
import com.saferide.monolith.user.model.UserMapper;
import com.saferide.monolith.user.model.Users;
import com.saferide.monolith.user.model.dtos.*;
import com.saferide.monolith.common.security.UserContext;
import com.saferide.monolith.kyc.service.KycGuard;
import com.saferide.monolith.user.repos.UserRepository;
import com.saferide.monolith.user.security.AttemptLimiter;
import com.saferide.monolith.user.security.JwtUtil;
import org.apache.coyote.BadRequestException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import jakarta.transaction.Transactional;

import java.util.UUID;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerificationService verificationService;
    private final AttemptLimiter attemptLimiter;
    private final KycGuard kycGuard;

    public UserService(UserRepository userRepository, UserMapper userMapper, AuthenticationManager authenticationManager, JwtUtil jwtUtil, PasswordEncoder passwordEncoder, EmailVerificationService verificationService,
                       AttemptLimiter attemptLimiter, KycGuard kycGuard) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
        this.passwordEncoder = passwordEncoder;
        this.verificationService = verificationService;
        this.attemptLimiter = attemptLimiter;
        this.kycGuard = kycGuard;
    }

    /**
     * Corrects the gender chosen at signup and hands back a fresh token.
     *
     * <p>Gender is a JWT claim and decides which rides an account can see, so
     * the old token would keep asserting the old value — the caller must swap
     * in the one returned here.
     *
     * <p>Refused once identity is verified: the value was matched against a
     * scanned CNIC, and letting it move afterwards would leave a verified
     * badge on a claim nobody checked.
     */
    @Transactional
    public LoginResponse changeGender(GenderChangeRequest request) {
        UserContext ctx = currentUser();
        if (kycGuard.isVerified(ctx)) {
            throw new GenderLockedException(
                    "Your gender was verified against your CNIC and can't be changed.");
        }
        Users user = userRepository.findById(ctx.userId())
                .orElseThrow(() -> new UserNotFoundException("User Not Found"));
        if (user.getRole() == null) {
            throw new UserNotFoundException("Finish signing up first");
        }
        user.setGender(Gender.valueOf(request.gender()));
        userRepository.save(user);
        return LoginResponse.builder()
                .token(jwtUtil.generateToken(user.getId(), user.getRole().name(),
                        user.getGender().name(), user.getEmail()))
                .build();
    }

    /** The caller, per the JWT the security filter validated. */
    private UserContext currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return (UserContext) authentication.getDetails();
    }

    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new UserAlreadyExistException("User already exists");
        }

        Users user = userMapper.toUser(request);
        user.setPassword(passwordEncoder.encode(request.password()));
        userRepository.save(user);
        verificationService.createAndSendVerification(user);
        UserResponse mapped = userMapper.toResponse(user);
        // The client persists this so it can still finish onboarding after the
        // app is killed while the user is away verifying their email.
        return new UserResponse(mapped.id(), mapped.email(),
                jwtUtil.generateOnboardingToken(user.getId()));
    }

    public LoginResponse login(LoginRequest request) throws BadRequestException {
        // Bounded before the password is ever hashed, so a brute-force run
        // can't spend the CPU either.
        attemptLimiter.check("sign in", request.email());
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
            throw new UserNotFoundException("Account is disabled");
        }
        if (!users.isEmailVerified()) {
            throw new UserNotFoundException("Please verify your email before logging in");
        }
        if (users.getRole() == null) {
            // Not an error: the credentials were right, onboarding just isn't
            // finished. Handing back the id and an onboarding token is the only
            // way a user whose app died mid-signup can ever pick a role — the
            // id is otherwise only returned by /register, which won't repeat.
            return LoginResponse.builder()
                    .userId(users.getId())
                    .roleRequired(true)
                    .onboardingToken(jwtUtil.generateOnboardingToken(users.getId()))
                    .build();
        }

        String token = jwtUtil.generateToken(
                users.getId(), users.getRole().name(), users.getGender().name(), users.getEmail());

        // A genuine sign-in clears the budget so a user who fat-fingered their
        // password a few times isn't left locked out.
        attemptLimiter.reset(request.email());
        return LoginResponse.builder()
                .token(token)
                .build();
    }

    /**
     * Sets the caller's role and issues their first real token. The user is
     * taken from the signed onboarding token rather than a path parameter —
     * the old {@code /{id}/select-role} was public and trusted the id as-is,
     * so anyone holding a stranger's UUID could set their role.
     */
    public LoginResponse selectRole(String onboardingToken, RoleSelection request) {
        UUID userId;
        try {
            userId = jwtUtil.parseOnboardingToken(onboardingToken);
        } catch (JwtException | IllegalArgumentException e) {
            throw new InvalidOnboardingTokenException(
                    "Your signup session expired. Please log in to continue.");
        }
        Users users = userRepository.findById(userId).orElseThrow(
                () -> new UsernameNotFoundException("User NotFound")
        );
        // The same gates login enforces. Without them, /register followed by
        // /select-role minted a full access token for an address the caller
        // never proved they own — "verified email" meant nothing.
        if (!users.isEnabled()) {
            throw new UserNotFoundException("Account is disabled");
        }
        if (!users.isEmailVerified()) {
            throw new UserNotFoundException("Please verify your email before choosing a role");
        }
        // The onboarding token stays valid for 24h; without this it could be
        // replayed afterwards to flip a settled account's role.
        if (users.getRole() != null) {
            throw new UserAlreadyExistException("A role has already been chosen for this account");
        }
        users.setRole(Role.valueOf(request.role()));
        Users updatedUser = userRepository.save(users);
        String token = jwtUtil.generateToken(
                updatedUser.getId(),
                updatedUser.getRole().name(),
                updatedUser.getGender().name(),
                updatedUser.getEmail()
        );
        return LoginResponse.builder()
                .token(token)
                .build();
    }

    public boolean isEmailVerified(String id) {
        Users users = userRepository.findById(UUID.fromString(id)).orElseThrow(
                () -> new UserNotFoundException("user not found")
        );
        return users.isEmailVerified();
    }
}
