package com.saferide.monolith.user.model.dtos;

import com.saferide.monolith.kyc.model.KycStatusResponse;
import lombok.Builder;

/**
 * Where a signup stands, and what the client needs to carry on. One shape for
 * every onboarding call so the app routes on a server-decided {@code stage}
 * instead of probing two or three endpoints and inferring — which is what used
 * to land half-finished users on the home screen.
 *
 * <p>{@code token} is null for every stage but {@link OnboardingStage#COMPLETE}:
 * a real session only exists once the account does.
 */
@Builder
public record OnboardingStateResponse(
        OnboardingStage stage,
        /** Authorises the next onboarding call; null once complete. */
        String onboardingToken,
        /** The real JWT — present only at {@link OnboardingStage#COMPLETE}. */
        String token,
        String email,
        String role,
        String gender,
        /**
         * What the user has typed so far. Echoed back so the edit screen can
         * prefill during signup — a KYC decline sends them straight there to
         * correct the CNIC or name the scanned card contradicted, and there is
         * no profile row yet to read it from.
         */
        String fullName,
        String phoneNo,
        String cnic,
        /** Driver only; null for passengers and before the vehicle step. */
        VehicleDetails vehicle,
        /** Populated by the KYC endpoints; null elsewhere. */
        KycStatusResponse kyc
) {

    /** The vehicle fields held with a pending driver signup. */
    public record VehicleDetails(String make, String model, String number,
                                 String color, Integer seats, Integer year) {
    }
}
