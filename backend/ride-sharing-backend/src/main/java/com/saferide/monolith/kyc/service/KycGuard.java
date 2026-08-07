package com.saferide.monolith.kyc.service;

import com.saferide.monolith.common.security.UserContext;
import com.saferide.monolith.kyc.exceptions.KycRequiredException;
import com.saferide.monolith.kyc.model.KycStatus;
import com.saferide.monolith.kyc.model.KycVerifiable;
import com.saferide.monolith.profile.repos.DriverProfileRepository;
import com.saferide.monolith.profile.repos.PassengerProfileRepository;
import org.springframework.stereotype.Component;

/**
 * Server-side enforcement of "identity verified before you ride". The app
 * also steers unverified users to the KYC screen, but that is only a
 * convenience — this is what actually makes the rule hold, since a client
 * can call the API directly.
 *
 * <p>Deliberately separate from {@link KycService}: the ride paths only need
 * to read a status, and must not drag {@code DiditClient} (and its outbound
 * HTTP call) into every ride request.
 */
@Component
public class KycGuard {

    private final DriverProfileRepository driverProfileRepository;
    private final PassengerProfileRepository passengerProfileRepository;

    public KycGuard(DriverProfileRepository driverProfileRepository,
                    PassengerProfileRepository passengerProfileRepository) {
        this.driverProfileRepository = driverProfileRepository;
        this.passengerProfileRepository = passengerProfileRepository;
    }

    /**
     * Lets the call through only for an APPROVED identity.
     *
     * @param action what the user was trying to do, woven into the error the
     *               app shows (e.g. "go online")
     * @throws KycRequiredException with a 403-mapped message otherwise
     */
    public void requireVerified(UserContext ctx, String action) {
        if (!isVerified(ctx)) {
            throw new KycRequiredException(
                    "Verify your identity before you can " + action + ".");
        }
    }

    public boolean isVerified(UserContext ctx) {
        KycVerifiable profile = "DRIVER".equals(ctx.role())
                ? driverProfileRepository.findByUserId(ctx.userId())
                : passengerProfileRepository.findByUserId(ctx.userId());
        // No profile yet means onboarding is incomplete — also not verified.
        return profile != null && profile.getKycStatus() == KycStatus.APPROVED;
    }
}
