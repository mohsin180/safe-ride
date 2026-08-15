package com.saferide.monolith.kyc.service;

import com.saferide.monolith.common.security.UserContext;
import com.saferide.monolith.kyc.client.DiditClient;
import com.saferide.monolith.kyc.model.KycStatus;
import com.saferide.monolith.kyc.model.KycStatusResponse;
import com.saferide.monolith.kyc.model.KycVerifiable;
import com.saferide.monolith.profile.exceptions.ProfileNotFoundException;
import com.saferide.monolith.profile.models.entities.DriverProfile;
import com.saferide.monolith.profile.models.entities.PassengerProfile;
import com.saferide.monolith.profile.repos.DriverProfileRepository;
import com.saferide.monolith.profile.repos.PassengerProfileRepository;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Identity verification (CNIC + liveness) via Didit. The flow is: the caller
 * asks us to start a session, we hand back Didit's hosted URL (the scan and
 * selfie happen there), then the caller polls while we poll Didit server-side
 * and fold the result into their record.
 *
 * <p>The subject of a verification is anything {@link KycVerifiable}: during
 * signup it's the {@code PendingSignup} row (no account exists yet — that's
 * the whole point), and afterwards it's the driver or passenger profile.
 * {@link #start} and {@link #refresh} hold the actual Didit logic and are
 * blind to which; the authenticated wrappers below just resolve the caller's
 * profile and persist. One code path, so the two entry points can't drift.
 */
@Service
public class KycService {

    private static final Logger log = LoggerFactory.getLogger(KycService.class);

    private final DiditClient diditClient;
    private final KycDocumentCheck documentCheck;
    private final DriverProfileRepository driverProfileRepository;
    private final PassengerProfileRepository passengerProfileRepository;
    private final boolean enforceDocumentMatch;

    public KycService(DiditClient diditClient,
                      KycDocumentCheck documentCheck,
                      DriverProfileRepository driverProfileRepository,
                      PassengerProfileRepository passengerProfileRepository,
                      @Value("${didit.enforce-document-match:false}") boolean enforceDocumentMatch) {
        this.diditClient = diditClient;
        this.documentCheck = documentCheck;
        this.driverProfileRepository = driverProfileRepository;
        this.passengerProfileRepository = passengerProfileRepository;
        this.enforceDocumentMatch = enforceDocumentMatch;
    }

    // ── Subject-agnostic core ───────────────────────────────────────────

    /**
     * Opens a Didit session against {@code subject}, mutating it in place;
     * the caller persists. Re-invoking after a decline or expiry starts a new
     * session; an already-approved subject is returned untouched.
     *
     * @param subjectId the reference handed to Didit — a pending signup id
     *                  during onboarding, a user id afterwards
     */
    public KycStatusResponse start(KycVerifiable subject, String subjectId) {
        if (currentStatus(subject) == KycStatus.APPROVED) {
            return KycStatusResponse.of(KycStatus.APPROVED, subject.getKycVerifiedAt());
        }
        DiditClient.DiditSession session = diditClient.createSession(subjectId);
        subject.setKycSessionId(session.sessionId());
        subject.setKycStatus(KycStatus.IN_PROGRESS);
        // A retry starts clean — the previous attempt's reason no longer applies.
        subject.setKycRejectionReason(null);
        return new KycStatusResponse(
                KycStatus.IN_PROGRESS.name(), session.token(), session.url(), null, null);
    }

    /**
     * Polls Didit for a non-terminal session and folds the decision into
     * {@code subject}, mutating it in place; the caller persists.
     *
     * @param accountGender the gender the account claims, cross-checked
     *                      against the scanned document
     * @return the status after the poll
     */
    public KycStatus refresh(KycVerifiable subject, String accountGender) {
        KycStatus status = currentStatus(subject);
        if (status == KycStatus.APPROVED || status == KycStatus.NOT_STARTED
                || subject.getKycSessionId() == null) {
            return status;
        }

        DiditClient.DiditDecision decision = diditClient.getDecision(subject.getKycSessionId());
        KycStatus mapped = mapDiditStatus(decision.status());
        String rejectionReason = null;

        if (mapped == KycStatus.APPROVED && shouldCrossCheck(decision)) {
            // Didit vouches for the document and the face; it has no idea
            // whether the account matches the person on the card. That last
            // step is ours.
            List<String> problems = documentCheck.findMismatches(decision, subject, accountGender);
            if (!problems.isEmpty()) {
                mapped = KycStatus.DECLINED;
                rejectionReason = "We couldn't verify you because "
                        + String.join(", and ", problems) + ".";
            }
        }

        if (mapped != status || !Objects.equals(rejectionReason, subject.getKycRejectionReason())) {
            subject.setKycStatus(mapped);
            subject.setKycRejectionReason(rejectionReason);
            if (mapped == KycStatus.APPROVED) {
                subject.setKycVerifiedAt(LocalDateTime.now());
            }
            if (mapped == KycStatus.NOT_STARTED) {
                // Session died (expired/abandoned) — clear it so a retry
                // starts clean.
                subject.setKycSessionId(null);
            }
        }
        return mapped;
    }

    /** The response shape for a subject in a given status. */
    public KycStatusResponse describe(KycVerifiable subject, KycStatus status) {
        return new KycStatusResponse(status.name(), null, null,
                subject.getKycVerifiedAt(), subject.getKycRejectionReason());
    }

    /** Existing rows predate the KYC columns, so a null status means NOT_STARTED. */
    public KycStatus currentStatus(KycVerifiable subject) {
        return subject.getKycStatus() == null ? KycStatus.NOT_STARTED : subject.getKycStatus();
    }

    // ── Authenticated (post-onboarding) entry points ────────────────────

    /**
     * Re-verification for an account that already exists — a profile edit that
     * changed a checked field, or an admin-forced recheck. Signup verification
     * does not come through here: no account exists at that point.
     */
    @Transactional
    public KycStatusResponse startVerification() {
        UserContext ctx = currentUser();
        KycVerifiable profile = requireProfile(ctx);
        KycStatusResponse response = start(profile, ctx.userId().toString());
        save(profile);
        return response;
    }

    @Transactional
    public KycStatusResponse getStatus() {
        UserContext ctx = currentUser();
        KycVerifiable profile = requireProfile(ctx);
        KycStatus status = refresh(profile, ctx.gender());
        save(profile);
        return describe(profile, status);
    }

    /**
     * Whether the scanned document may be compared against the account.
     *
     * <p>Sandbox sessions always return the same synthetic document (a
     * Brazilian passport for "Gustavo Matsumoto"), which by construction can
     * never match a real Pakistani CNIC — so cross-checking there rejects
     * everyone forever, no matter how many times they correct their details.
     * Live documents are the user's own, so the checks apply.
     *
     * <p>Set {@code didit.enforce-document-match=true} to run them in sandbox
     * anyway, which is how the mismatch rejection can be demonstrated.
     */
    private boolean shouldCrossCheck(DiditClient.DiditDecision decision) {
        if (!"sandbox".equalsIgnoreCase(decision.environment()) || enforceDocumentMatch) {
            return true;
        }
        log.info("Sandbox verification approved — skipping document cross-checks "
                + "(sandbox returns a synthetic document that cannot match a real CNIC)");
        return false;
    }

    /**
     * Collapses Didit's case-sensitive session statuses into our state
     * machine. Anything still moving ("In Progress", "Awaiting User",
     * "Resubmitted", …) stays IN_PROGRESS.
     */
    private KycStatus mapDiditStatus(String diditStatus) {
        return switch (diditStatus) {
            case "Approved" -> KycStatus.APPROVED;
            case "Declined" -> KycStatus.DECLINED;
            case "In Review" -> KycStatus.IN_REVIEW;
            case "Expired", "Abandoned", "Kyc Expired" -> KycStatus.NOT_STARTED;
            default -> KycStatus.IN_PROGRESS;
        };
    }

    /** The driver or passenger profile of the caller, per their JWT role. */
    private KycVerifiable requireProfile(UserContext ctx) {
        KycVerifiable profile = "DRIVER".equals(ctx.role())
                ? driverProfileRepository.findByUserId(ctx.userId())
                : passengerProfileRepository.findByUserId(ctx.userId());
        if (profile == null) {
            throw new ProfileNotFoundException("Create your profile before verifying your identity");
        }
        return profile;
    }

    private void save(KycVerifiable profile) {
        if (profile instanceof DriverProfile driver) {
            driverProfileRepository.save(driver);
        } else {
            passengerProfileRepository.save((PassengerProfile) profile);
        }
    }

    private UserContext currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return (UserContext) authentication.getDetails();
    }
}
