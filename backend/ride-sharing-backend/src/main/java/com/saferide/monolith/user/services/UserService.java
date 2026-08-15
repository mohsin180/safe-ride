package com.saferide.monolith.user.services;

import com.saferide.monolith.user.exceptions.InvalidOnboardingTokenException;
import com.saferide.monolith.user.exceptions.UserAlreadyExistException;
import com.saferide.monolith.user.exceptions.GenderLockedException;
import com.saferide.monolith.user.exceptions.UserNotFoundException;
import com.saferide.monolith.user.model.PendingSignup;
import com.saferide.monolith.user.model.Users;
import com.saferide.monolith.user.model.dtos.*;
import com.saferide.monolith.common.security.UserContext;
import com.saferide.monolith.kyc.service.KycGuard;
import com.saferide.monolith.user.repos.PendingSignupRepository;
import com.saferide.monolith.user.repos.UserRepository;
import com.saferide.monolith.user.security.AttemptLimiter;
import com.saferide.monolith.user.security.JwtUtil;
import org.apache.coyote.BadRequestException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import jakarta.transaction.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final PendingSignupRepository pendingSignupRepository;
    private final OnboardingService onboardingService;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final AttemptLimiter attemptLimiter;
    private final KycGuard kycGuard;

    public UserService(UserRepository userRepository,
                       PendingSignupRepository pendingSignupRepository,
                       OnboardingService onboardingService,
                       AuthenticationManager authenticationManager, JwtUtil jwtUtil,
                       PasswordEncoder passwordEncoder,
                       AttemptLimiter attemptLimiter, KycGuard kycGuard) {
        this.userRepository = userRepository;
        this.pendingSignupRepository = pendingSignupRepository;
        this.onboardingService = onboardingService;
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
        this.passwordEncoder = passwordEncoder;
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

    /**
     * Signs in.
     *
     * <p>Two populations can answer to an address: a finished account in
     * {@code users}, and a signup still in flight in {@code pending_signup}.
     * The second is checked first and never authenticates through Spring
     * Security — that {@code UserDetailsService} only knows real accounts, and
     * a pending signup isn't one yet. Matching the password by hand here is
     * what lets someone who reinstalled the app resume onboarding with nothing
     * but their credentials.
     */
    public LoginResponse login(LoginRequest request) throws BadRequestException {
        // Bounded before any password is ever hashed, so a brute-force run
        // can't spend the CPU either.
        attemptLimiter.check("sign in", request.email());

        Optional<PendingSignup> pending = pendingSignupRepository.findByEmail(request.email());
        if (pending.isPresent()) {
            PendingSignup signup = pending.get();
            if (!passwordEncoder.matches(request.password(), signup.getPassword())) {
                throw new BadCredentialsException("Invalid email or password");
            }
            attemptLimiter.reset(request.email());
            // No token: there is no account to hold a session yet. The stage
            // tells the app which onboarding screen to reopen.
            OnboardingStateResponse state = onboardingService.stateFor(signup);
            return LoginResponse.builder()
                    .userId(signup.getId())
                    .onboardingStage(state.stage())
                    .onboardingToken(state.onboardingToken())
                    .build();
        }

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

        String token = jwtUtil.generateToken(
                users.getId(), users.getRole().name(), users.getGender().name(), users.getEmail());

        // A genuine sign-in clears the budget so a user who fat-fingered their
        // password a few times isn't left locked out.
        attemptLimiter.reset(request.email());
        return LoginResponse.builder()
                .token(token)
                .onboardingStage(OnboardingStage.COMPLETE)
                .build();
    }

    /**
     * Whether the signup behind this id has clicked its verification link.
     * Polled by the app while the user is away in their mail client.
     *
     * <p>Answers about {@code pending_signup}, not {@code users}: a row in
     * {@code users} is only ever created after verification, so asking there
     * could only ever return true and would tell the caller nothing.
     */
    public boolean isEmailVerified(String id) {
        return pendingSignupRepository.findById(UUID.fromString(id))
                .map(PendingSignup::isEmailVerified)
                // No pending row and a well-formed id means the signup already
                // finished and was promoted — verified by construction.
                .orElseGet(() -> userRepository.existsById(UUID.fromString(id)));
    }
}
