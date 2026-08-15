package com.saferide.monolith.user.model;

import com.saferide.monolith.kyc.model.KycStatus;
import com.saferide.monolith.kyc.model.KycVerifiable;
import com.saferide.monolith.user.model.dtos.Gender;
import com.saferide.monolith.user.model.dtos.OnboardingStage;
import com.saferide.monolith.user.model.dtos.Role;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A signup that hasn't finished yet. Everything the user types on the way in —
 * credentials, role, profile, vehicle, KYC progress — lives here and nowhere
 * else, until identity verification is APPROVED.
 *
 * <p>Only at that moment does {@code OnboardingService.promote} turn this row
 * into a real {@link Users} row plus its profile (and vehicle, for drivers),
 * and delete this one. So the {@code users} table only ever holds accounts
 * that are complete: email verified, role chosen, profile filled, identity
 * proven. There is no such thing as a half-made account to reason about, and
 * nothing downstream needs to check for one.
 *
 * <p>The consequence worth knowing: an unfinished signup never holds a real
 * JWT — only the short-lived onboarding token — so every protected endpoint
 * already refuses it without a single extra guard. The KYC gate is not
 * something the ride endpoints enforce; it's something the account model
 * makes unreachable.
 *
 * <p>Implements {@link KycVerifiable} so {@code KycService} runs the exact
 * same Didit session / polling / document-cross-check code for a pending
 * signup as it does for an approved user editing their profile later.
 */
@Table(name = "pending_signup")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
public class PendingSignup implements KycVerifiable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Unique here for the same reason it is on {@link Users}: two concurrent
     * registrations for one address would otherwise both survive and make
     * every later lookup ambiguous. Uniqueness across the two tables is
     * enforced in {@code UserService.register}, which checks both.
     */
    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Gender gender;

    /** Null until the role-selection step. */
    @Enumerated(EnumType.STRING)
    private Role role;

    @Column(nullable = false)
    private boolean emailVerified = false;

    // ── Profile step (both roles) ───────────────────────────────────────
    private String fullName;
    private String phoneNo;
    private String cnic;

    // ── Vehicle step (drivers only) ─────────────────────────────────────
    private String vehicleMake;
    private String vehicleModel;
    private String vehicleNumber;
    private String vehicleColor;
    private Integer vehicleSeats;
    private Integer vehicleYear;

    // ── KYC step ────────────────────────────────────────────────────────
    @Enumerated(EnumType.STRING)
    private KycStatus kycStatus = KycStatus.NOT_STARTED;
    private String kycSessionId;
    private LocalDateTime kycVerifiedAt;
    @Column(length = 500)
    private String kycRejectionReason;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    /**
     * How far along this signup is — derived, never stored, so it can't drift
     * out of step with the columns it describes. The client routes on this
     * instead of probing two or three endpoints and guessing from what
     * answers, which is what used to drop people on the wrong screen.
     */
    public OnboardingStage stage() {
        if (!emailVerified) {
            return OnboardingStage.EMAIL_VERIFICATION;
        }
        if (role == null) {
            return OnboardingStage.ROLE;
        }
        if (fullName == null || fullName.isBlank()) {
            return OnboardingStage.PROFILE;
        }
        // A driver without vehicle details hasn't finished the profile step;
        // the two screens are one stage as far as routing is concerned.
        if (role == Role.DRIVER && (vehicleNumber == null || vehicleNumber.isBlank())) {
            return OnboardingStage.VEHICLE;
        }
        return OnboardingStage.KYC;
    }
}
