package com.saferide.monolith.user.services;

import com.saferide.monolith.kyc.model.KycStatus;
import com.saferide.monolith.kyc.model.KycStatusResponse;
import com.saferide.monolith.kyc.service.KycService;
import com.saferide.monolith.profile.models.dtos.DriverProfileRequest;
import com.saferide.monolith.profile.models.dtos.PassengerProfileRequest;
import com.saferide.monolith.profile.models.entities.DriverProfile;
import com.saferide.monolith.profile.models.entities.PassengerProfile;
import com.saferide.monolith.profile.models.entities.Vehicle;
import com.saferide.monolith.profile.repos.DriverProfileRepository;
import com.saferide.monolith.profile.repos.PassengerProfileRepository;
import com.saferide.monolith.profile.repos.VehicleRepository;
import com.saferide.monolith.user.exceptions.InvalidOnboardingTokenException;
import com.saferide.monolith.user.exceptions.UserAlreadyExistException;
import com.saferide.monolith.user.model.PendingSignup;
import com.saferide.monolith.user.model.Users;
import com.saferide.monolith.user.model.dtos.*;
import com.saferide.monolith.user.repos.EmailVerificationTokenRepo;
import com.saferide.monolith.user.repos.PendingSignupRepository;
import com.saferide.monolith.user.repos.UserRepository;
import com.saferide.monolith.user.security.JwtUtil;
import io.jsonwebtoken.JwtException;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Owns the whole way in: register → verify email → pick a role → fill the
 * profile (and vehicle) → prove identity. Every one of those steps writes to
 * a {@link PendingSignup} row and nothing else.
 *
 * <p>{@link #promote} is the only place in the system that creates a
 * {@code users} row, and it runs at exactly one moment: the poll that first
 * sees KYC APPROVED. So "account exists" and "account is fully verified" are
 * the same statement — there is no partial account for any other code to
 * encounter, and no gate for anyone to forget to apply.
 *
 * <p>Everything here is authorised by the onboarding token, which carries no
 * role and is rejected by {@code JwtAuthFilter} for ordinary API calls. An
 * unfinished signup therefore cannot touch a ride endpoint even by calling the
 * API directly — not because those endpoints check, but because it holds
 * nothing they accept.
 */
@Service
@Slf4j
public class OnboardingService {

    private final PendingSignupRepository pendingRepo;
    private final UserRepository userRepository;
    private final PassengerProfileRepository passengerProfileRepository;
    private final DriverProfileRepository driverProfileRepository;
    private final VehicleRepository vehicleRepository;
    private final EmailVerificationTokenRepo verificationTokenRepo;
    private final EmailVerificationService verificationService;
    private final KycService kycService;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public OnboardingService(PendingSignupRepository pendingRepo,
                             UserRepository userRepository,
                             PassengerProfileRepository passengerProfileRepository,
                             DriverProfileRepository driverProfileRepository,
                             VehicleRepository vehicleRepository,
                             EmailVerificationTokenRepo verificationTokenRepo,
                             EmailVerificationService verificationService,
                             KycService kycService,
                             PasswordEncoder passwordEncoder,
                             JwtUtil jwtUtil) {
        this.pendingRepo = pendingRepo;
        this.userRepository = userRepository;
        this.passengerProfileRepository = passengerProfileRepository;
        this.driverProfileRepository = driverProfileRepository;
        this.vehicleRepository = vehicleRepository;
        this.verificationTokenRepo = verificationTokenRepo;
        this.verificationService = verificationService;
        this.kycService = kycService;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    // ── Step 1: register ────────────────────────────────────────────────

    @Transactional
    public UserResponse register(RegisterRequest request) {
        // Both tables, because an address is taken whether it belongs to a
        // finished account or a signup still in flight.
        if (userRepository.existsByEmail(request.email())
                || pendingRepo.existsByEmail(request.email())) {
            throw new UserAlreadyExistException("User already exists");
        }
        PendingSignup signup = new PendingSignup();
        signup.setEmail(request.email());
        signup.setPassword(passwordEncoder.encode(request.password()));
        signup.setGender(Gender.valueOf(request.gender()));
        pendingRepo.save(signup);
        verificationService.createAndSendVerification(signup);
        log.info("Signup started id={}", signup.getId());
        return new UserResponse(signup.getId(), signup.getEmail(),
                jwtUtil.generateOnboardingToken(signup.getId()));
    }

    // ── Step 2: role ────────────────────────────────────────────────────

    @Transactional
    public OnboardingStateResponse selectRole(String onboardingToken, RoleSelection request) {
        PendingSignup signup = require(onboardingToken);
        requireEmailVerified(signup);
        if (signup.getRole() != null) {
            throw new UserAlreadyExistException("A role has already been chosen for this account");
        }
        signup.setRole(Role.valueOf(request.role()));
        pendingRepo.save(signup);
        return state(signup, null);
    }

    // ── Step 3: profile (+ vehicle for drivers) ─────────────────────────

    @Transactional
    public OnboardingStateResponse savePassengerProfile(String onboardingToken,
                                                        PassengerProfileRequest request) {
        PendingSignup signup = require(onboardingToken);
        requireRole(signup, Role.PASSENGER);
        signup.setFullName(request.fullName());
        signup.setPhoneNo(request.phoneNo());
        signup.setCnic(request.cnic());
        pendingRepo.save(signup);
        return state(signup, null);
    }

    /**
     * Driver profile and vehicle arrive together, matching
     * {@link DriverProfileRequest}. The app collects them on two screens, but
     * only sends once both are filled.
     */
    @Transactional
    public OnboardingStateResponse saveDriverProfile(String onboardingToken,
                                                     DriverProfileRequest request) {
        PendingSignup signup = require(onboardingToken);
        requireRole(signup, Role.DRIVER);
        signup.setFullName(request.fullName());
        signup.setPhoneNo(request.phoneNo());
        signup.setCnic(request.cnic());
        signup.setVehicleMake(request.vehicle().make());
        signup.setVehicleModel(request.vehicle().model());
        signup.setVehicleNumber(request.vehicle().number());
        signup.setVehicleColor(request.vehicle().color());
        signup.setVehicleSeats(request.vehicle().seats());
        signup.setVehicleYear(request.vehicle().year());
        pendingRepo.save(signup);
        return state(signup, null);
    }

    /**
     * Corrects the gender picked at registration, which is the fix for the
     * commonest KYC rejection: the CNIC says one thing and the account says
     * another. Free to change here precisely because nothing has been verified
     * yet — once KYC approves, {@code UserService.changeGender} refuses.
     */
    @Transactional
    public OnboardingStateResponse changeGender(String onboardingToken,
                                                GenderChangeRequest request) {
        PendingSignup signup = require(onboardingToken);
        signup.setGender(Gender.valueOf(request.gender()));
        pendingRepo.save(signup);
        return state(signup, null);
    }

    // ── Step 4: identity ────────────────────────────────────────────────

    @Transactional
    public OnboardingStateResponse startKyc(String onboardingToken) {
        PendingSignup signup = require(onboardingToken);
        requireProfileComplete(signup);
        KycStatusResponse kyc = kycService.start(signup, signup.getId().toString());
        pendingRepo.save(signup);
        return state(signup, kyc);
    }

    /**
     * Polls Didit and, the first time the answer is APPROVED, promotes the
     * signup into a real account and hands back its token. The client swaps
     * that in and is done — there is no separate "finish" call to lose.
     */
    @Transactional
    public OnboardingStateResponse kycStatus(String onboardingToken) {
        PendingSignup signup = require(onboardingToken);
        KycStatus status = kycService.refresh(signup, signup.getGender().name());
        if (status != KycStatus.APPROVED) {
            pendingRepo.save(signup);
            return state(signup, kycService.describe(signup, status));
        }
        String token = promote(signup);
        return OnboardingStateResponse.builder()
                .stage(OnboardingStage.COMPLETE)
                .token(token)
                .email(signup.getEmail())
                .role(signup.getRole().name())
                .gender(signup.getGender().name())
                .kyc(kycService.describe(signup, KycStatus.APPROVED))
                .build();
    }

    // ── Resume ──────────────────────────────────────────────────────────

    /** Where this signup left off — what the app asks on a cold start. */
    @Transactional
    public OnboardingStateResponse currentState(String onboardingToken) {
        return state(require(onboardingToken), null);
    }

    /**
     * The state for a signup found by login rather than by token, so someone
     * who reinstalled the app can pick up where they left off with only their
     * password.
     */
    public OnboardingStateResponse stateFor(PendingSignup signup) {
        return state(signup, null);
    }

    // ── Promotion: the one place a real account is born ─────────────────

    /**
     * Turns a verified signup into {@code users} + profile (+ vehicle) rows,
     * deletes the staging row, and returns the account's first real JWT.
     *
     * <p>Single transaction with the caller: if any part fails, the signup
     * stays pending and the next poll simply tries again. A half-promoted
     * account — the exact thing this design exists to prevent — can't survive
     * a rollback.
     */
    private String promote(PendingSignup signup) {
        Users user = new Users();
        user.setEmail(signup.getEmail());
        user.setPassword(signup.getPassword());   // already encoded at register
        user.setGender(signup.getGender());
        user.setRole(signup.getRole());
        user.setEnabled(true);
        // Verified long before we got here; a promoted account is by
        // definition past that gate.
        user.setEmailVerified(true);
        Users saved = userRepository.save(user);

        if (signup.getRole() == Role.DRIVER) {
            createDriverProfile(signup, saved.getId());
        } else {
            createPassengerProfile(signup, saved.getId());
        }

        // The FK from the verification tokens would refuse the delete.
        verificationTokenRepo.deleteByPendingId(signup.getId());
        pendingRepo.delete(signup);

        log.info("Signup promoted to account id={} role={}", saved.getId(), saved.getRole());
        return jwtUtil.generateToken(saved.getId(), saved.getRole().name(),
                saved.getGender().name(), saved.getEmail());
    }

    private void createPassengerProfile(PendingSignup signup, UUID userId) {
        PassengerProfile profile = new PassengerProfile();
        profile.setUserId(userId);
        profile.setFullName(signup.getFullName());
        profile.setPhoneNo(signup.getPhoneNo());
        profile.setCnic(signup.getCnic());
        profile.setRating(0.0);
        carryKyc(signup, profile);
        passengerProfileRepository.save(profile);
    }

    private void createDriverProfile(PendingSignup signup, UUID userId) {
        DriverProfile profile = new DriverProfile();
        profile.setUserId(userId);
        profile.setFullName(signup.getFullName());
        profile.setPhoneNo(signup.getPhoneNo());
        profile.setCnic(signup.getCnic());
        profile.setRating(0.0);
        carryKyc(signup, profile);
        DriverProfile savedProfile = driverProfileRepository.save(profile);

        Vehicle vehicle = new Vehicle();
        vehicle.setMake(signup.getVehicleMake());
        vehicle.setModel(signup.getVehicleModel());
        vehicle.setNumber(signup.getVehicleNumber());
        vehicle.setColor(signup.getVehicleColor());
        vehicle.setSeats(signup.getVehicleSeats());
        vehicle.setYear(signup.getVehicleYear());
        vehicle.setDriverProfile(savedProfile);
        vehicleRepository.save(vehicle);
    }

    /**
     * Copies the verification outcome onto the profile, so the badge, the
     * verified-at date and {@code KycGuard} all read APPROVED from the first
     * moment the account exists — rather than the profile starting at
     * NOT_STARTED and making a just-verified user re-verify.
     */
    private void carryKyc(PendingSignup signup, com.saferide.monolith.kyc.model.KycVerifiable profile) {
        profile.setKycStatus(KycStatus.APPROVED);
        profile.setKycSessionId(signup.getKycSessionId());
        profile.setKycVerifiedAt(signup.getKycVerifiedAt());
        profile.setKycRejectionReason(null);
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private OnboardingStateResponse state(PendingSignup signup, KycStatusResponse kyc) {
        return OnboardingStateResponse.builder()
                .stage(signup.stage())
                .onboardingToken(jwtUtil.generateOnboardingToken(signup.getId()))
                .email(signup.getEmail())
                .role(signup.getRole() != null ? signup.getRole().name() : null)
                .gender(signup.getGender().name())
                .fullName(signup.getFullName())
                .phoneNo(signup.getPhoneNo())
                .cnic(signup.getCnic())
                .vehicle(vehicleOf(signup))
                .kyc(kyc)
                .build();
    }

    private OnboardingStateResponse.VehicleDetails vehicleOf(PendingSignup signup) {
        if (signup.getVehicleNumber() == null || signup.getVehicleNumber().isBlank()) {
            return null;
        }
        return new OnboardingStateResponse.VehicleDetails(
                signup.getVehicleMake(), signup.getVehicleModel(),
                signup.getVehicleNumber(), signup.getVehicleColor(),
                signup.getVehicleSeats(), signup.getVehicleYear());
    }

    /** The signup a valid onboarding token points at. */
    private PendingSignup require(String onboardingToken) {
        UUID id;
        try {
            id = jwtUtil.parseOnboardingToken(onboardingToken);
        } catch (JwtException | IllegalArgumentException e) {
            throw new InvalidOnboardingTokenException(
                    "Your signup session expired. Please log in to continue.");
        }
        return pendingRepo.findById(id).orElseThrow(() -> new InvalidOnboardingTokenException(
                "Your signup session expired. Please log in to continue."));
    }

    private void requireEmailVerified(PendingSignup signup) {
        if (!signup.isEmailVerified()) {
            throw new InvalidOnboardingTokenException(
                    "Please verify your email before continuing");
        }
    }

    private void requireRole(PendingSignup signup, Role expected) {
        requireEmailVerified(signup);
        if (signup.getRole() != expected) {
            throw new InvalidOnboardingTokenException(
                    "Choose your role before filling in your profile");
        }
    }

    private void requireProfileComplete(PendingSignup signup) {
        OnboardingStage stage = signup.stage();
        if (stage != OnboardingStage.KYC) {
            throw new InvalidOnboardingTokenException(
                    "Finish your profile before verifying your identity");
        }
    }
}
