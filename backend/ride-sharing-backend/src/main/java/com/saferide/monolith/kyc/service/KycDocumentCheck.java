package com.saferide.monolith.kyc.service;

import com.saferide.monolith.kyc.client.DiditClient;
import com.saferide.monolith.kyc.model.KycVerifiable;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Cross-checks the CNIC Didit actually read against what the user claimed
 * when they signed up. Didit proves the card is genuine and that the person
 * holding it is alive and matches its photo — it does <em>not</em> know that
 * the account claiming to be a woman was registered by a man, or that the
 * CNIC typed into the profile belongs to someone else entirely. That gap is
 * what this closes, and it matters here because SafeRide matches riders by
 * gender.
 *
 * <p>Design rule: a check only fires when Didit actually gave us the field.
 * Missing data is "unknown", never a rejection — otherwise a document that
 * simply doesn't print a field would lock out a legitimate user.
 */
@Component
public class KycDocumentCheck {

    /** Minimum age to use the platform. */
    private static final int MIN_AGE = 18;

    /**
     * Reasons the scanned document contradicts the account, empty when it all
     * lines up. The caller turns a non-empty list into a rejection.
     *
     * @param accountGender the gender chosen at signup ("MALE"/"FEMALE"),
     *                      taken from the JWT
     */
    public List<String> findMismatches(DiditClient.DiditDecision decision,
                                       KycVerifiable profile,
                                       String accountGender) {
        List<String> problems = new ArrayList<>();

        if (isGenderMismatch(decision.gender(), accountGender)) {
            problems.add("the gender on your CNIC doesn't match the one on your account");
        }
        if (isCnicMismatch(decision.documentNumber(), profile.getCnic())) {
            problems.add("the CNIC number you entered doesn't match the card you scanned");
        }
        if (isUnderage(decision.dateOfBirth())) {
            problems.add("you must be at least " + MIN_AGE + " years old");
        }
        if (decision.warningRisks().contains("DOCUMENT_EXPIRED")) {
            problems.add("your CNIC has expired");
        }
        return problems;
    }

    /**
     * Documents print a single letter ("M"/"F"); accounts store MALE/FEMALE.
     * Anything we can't confidently read — a blank, or a value like "X" —
     * is left alone rather than treated as a contradiction.
     */
    private boolean isGenderMismatch(String documentGender, String accountGender) {
        if (documentGender == null || accountGender == null) {
            return false;
        }
        Boolean documentIsMale = toIsMale(documentGender);
        Boolean accountIsMale = toIsMale(accountGender);
        if (documentIsMale == null || accountIsMale == null) {
            return false;
        }
        return !documentIsMale.equals(accountIsMale);
    }

    private Boolean toIsMale(String value) {
        String v = value.trim().toUpperCase();
        if (v.startsWith("M")) {
            return true;
        }
        if (v.startsWith("F")) {
            return false;
        }
        return null;
    }

    /**
     * Profiles hold a formatted CNIC ("12345-1234567-1") while the card may
     * come back unformatted, so both sides are reduced to digits first.
     */
    private boolean isCnicMismatch(String documentNumber, String profileCnic) {
        String scanned = digitsOnly(documentNumber);
        String claimed = digitsOnly(profileCnic);
        if (scanned.isEmpty() || claimed.isEmpty()) {
            return false;
        }
        return !scanned.equals(claimed);
    }

    private String digitsOnly(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }

    /** Reads the ISO date Didit returns; an unparseable one is ignored. */
    private boolean isUnderage(String dateOfBirth) {
        if (dateOfBirth == null) {
            return false;
        }
        try {
            LocalDate dob = LocalDate.parse(dateOfBirth.substring(0, 10));
            return Period.between(dob, LocalDate.now()).getYears() < MIN_AGE;
        } catch (DateTimeParseException | IndexOutOfBoundsException e) {
            return false;
        }
    }
}
