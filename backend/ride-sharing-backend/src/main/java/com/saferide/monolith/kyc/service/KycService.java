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
 * Identity verification (CNIC + liveness) via Didit, for drivers and
 * passengers alike. The flow is: the app asks us to start a session, we hand
 * back Didit's hosted URL (the scan and selfie happen there), then the app
 * polls {@link #getStatus()} while we poll Didit server-side and fold the
 * result into the caller's profile.
 *
 * <p>Which profile is touched follows the JWT's role, so one pair of
 * endpoints serves both onboarding wizards.
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

    /**
     * Creates a fresh Didit session for the authenticated user and returns
     * the hosted verification URL. Re-invoking after a decline/expiry simply
     * starts a new session; an already-approved user gets APPROVED back
     * without a new session.
     */
    @Transactional
    public KycStatusResponse startVerification() {
        UserContext ctx = currentUser();
        KycVerifiable profile = requireProfile(ctx);
        if (currentStatus(profile) == KycStatus.APPROVED) {
            return KycStatusResponse.of(KycStatus.APPROVED, profile.getKycVerifiedAt());
        }
        DiditClient.DiditSession session = diditClient.createSession(ctx.userId().toString());
        profile.setKycSessionId(session.sessionId());
        profile.setKycStatus(KycStatus.IN_PROGRESS);
        // A retry starts clean — the previous attempt's reason no longer applies.
        profile.setKycRejectionReason(null);
        save(profile);
        return new KycStatusResponse(
                KycStatus.IN_PROGRESS.name(), session.token(), session.url(), null, null);
    }

    /**
     * The caller's current KYC state. Non-terminal states trigger a live poll
     * of Didit's decision endpoint; the mapped result is persisted so the
     * status survives restarts and is visible on the profile.
     */
    @Transactional
    public KycStatusResponse getStatus() {
        UserContext ctx = currentUser();
        KycVerifiable profile = requireProfile(ctx);
        KycStatus status = currentStatus(profile);

        if (status == KycStatus.APPROVED || status == KycStatus.NOT_STARTED
                || profile.getKycSessionId() == null) {
            return response(profile, status);
        }

        DiditClient.DiditDecision decision = diditClient.getDecision(profile.getKycSessionId());
        KycStatus mapped = mapDiditStatus(decision.status());
        String rejectionReason = null;

        if (mapped == KycStatus.APPROVED && shouldCrossCheck(decision)) {
            // Didit vouches for the document and the face; it has no idea
            // whether the account matches the person on the card. That last
            // step is ours.
            List<String> problems = documentCheck.findMismatches(decision, profile, ctx.gender());
            if (!problems.isEmpty()) {
                mapped = KycStatus.DECLINED;
                rejectionReason = "We couldn't verify you because "
                        + String.join(", and ", problems) + ".";
            }
        }

        if (mapped != status || !Objects.equals(rejectionReason, profile.getKycRejectionReason())) {
            profile.setKycStatus(mapped);
            profile.setKycRejectionReason(rejectionReason);
            if (mapped == KycStatus.APPROVED) {
                profile.setKycVerifiedAt(LocalDateTime.now());
            }
            if (mapped == KycStatus.NOT_STARTED) {
                // Session died (expired/abandoned) — clear it so a retry
                // starts clean.
                profile.setKycSessionId(null);
            }
            save(profile);
        }
        return response(profile, mapped);
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

    private KycStatusResponse response(KycVerifiable profile, KycStatus status) {
        return new KycStatusResponse(status.name(), null, null,
                profile.getKycVerifiedAt(), profile.getKycRejectionReason());
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

    /** Existing rows predate the KYC columns, so a null status means NOT_STARTED. */
    private KycStatus currentStatus(KycVerifiable profile) {
        return profile.getKycStatus() == null ? KycStatus.NOT_STARTED : profile.getKycStatus();
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
