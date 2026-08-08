package com.saferide.monolith.kyc.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Server-side client for the Didit identity-verification API
 * (https://docs.didit.me). Two calls are used:
 * <pre>
 *   POST {base-url}/v3/session/                    — create a verification session
 *   GET  {base-url}/v3/session/{id}/decision/      — poll the session's status/decision
 * </pre>
 * Both authenticate with the {@code x-api-key} header. The API key never
 * leaves this backend — the Flutter app only ever receives the hosted
 * verification URL. We poll the decision endpoint instead of registering a
 * webhook because local dev has no publicly reachable HTTPS endpoint; the
 * server-to-server poll is equally authoritative.
 */
@Component
public class DiditClient {

    private static final Logger log = LoggerFactory.getLogger(DiditClient.class);

    private static final ParameterizedTypeReference<Map<String, Object>> JSON_TYPE =
            new ParameterizedTypeReference<>() {};

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RestClient client;
    private final String apiKey;
    private final String workflowId;

    public DiditClient(
            @Value("${didit.api-key:}") String apiKey,
            @Value("${didit.base-url:https://verification.didit.me}") String baseUrl,
            @Value("${didit.workflow-id:}") String workflowId) {
        this.apiKey = apiKey;
        this.workflowId = workflowId;
        this.client = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("x-api-key", apiKey)
                // Registered explicitly: the bare builder's defaults can't read
                // Didit's JSON responses. Slotted in as the JSON converter
                // rather than first in the list, so the byte[] converter still
                // handles the pre-serialised body — otherwise the payload gets
                // JSON-encoded a second time and arrives as a quoted string.
                .configureMessageConverters(c -> c
                        .registerDefaults()
                        .withJsonConverter(new JacksonJsonHttpMessageConverter()))
                .build();
    }

    /**
     * A freshly created verification session.
     *
     * @param sessionId Didit's id, used to poll the decision
     * @param url       hosted flow URL — the browser/WebView fallback
     * @param token     short-lived session token the native mobile SDKs take
     */
    public record DiditSession(String sessionId, String url, String token) {}

    /**
     * Creates a verification session for the given driver.
     *
     * @param vendorData our internal user id — Didit echoes it back on the
     *                   session so results are attributable to the driver
     */
    public DiditSession createSession(String vendorData) {
        requireConfigured();
        try {
            // Serialised here rather than handed over as a Map on purpose:
            // a String body carries a Content-Length, whereas an object body
            // is streamed as "Transfer-Encoding: chunked". Didit reads a
            // chunked body as empty and rejects the call with
            // "workflow_id: This field is required", even though the JSON is
            // on the wire.
            byte[] payload = MAPPER.writeValueAsBytes(Map.of(
                    "workflow_id", workflowId,
                    "vendor_data", vendorData));
            Map<String, Object> body = client.post()
                    .uri("/v3/session/")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(JSON_TYPE);
            if (body == null || body.get("session_id") == null || body.get("url") == null) {
                throw new IllegalStateException("Didit returned an unexpected session payload");
            }
            Object token = body.get("session_token");
            return new DiditSession(
                    body.get("session_id").toString(),
                    body.get("url").toString(),
                    token == null ? null : token.toString());
        } catch (RestClientResponseException e) {
            log.warn("Didit create-session failed: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new IllegalStateException(
                    "Identity verification service rejected the request (" + e.getStatusCode().value()
                            + ") — check DIDIT_API_KEY");
        }
    }

    /**
     * A session's verdict plus the document data we cross-check against the
     * user's own profile.
     *
     * <p>Every field except {@code status} may be null: the arrays are absent
     * until the user actually finishes the flow, and not every document
     * carries every field. Callers must treat null as "unknown", never as a
     * mismatch.
     *
     * @param status       case-sensitive Didit literal — "Not Started",
     *                     "In Progress", "Awaiting User", "In Review",
     *                     "Approved", "Declined", "Resubmitted", "Abandoned",
     *                     "Expired", "Kyc Expired"
     * @param gender       as printed on the document, typically "M" or "F"
     * @param documentNumber the CNIC number read off the card
     * @param dateOfBirth  ISO date string
     * @param warningRisks stable risk codes such as {@code DOCUMENT_EXPIRED}
     * @param environment  {@code "sandbox"} or {@code "live"} — sandbox hands
     *                     back a synthetic document, so its fields must not be
     *                     compared against the real account
     */
    public record DiditDecision(String status, String gender, String documentNumber,
                                String fullName, String dateOfBirth, List<String> warningRisks,
                                String environment) {}

    /** Fetches a session's verdict and the document data extracted from it. */
    public DiditDecision getDecision(String sessionId) {
        requireConfigured();
        Map<String, Object> body;
        try {
            body = client.get()
                    .uri("/v3/session/{id}/decision/", sessionId)
                    .retrieve()
                    .body(JSON_TYPE);
        } catch (RestClientResponseException e) {
            log.warn("Didit decision poll failed for session {}: {} {}",
                    sessionId, e.getStatusCode(), e.getResponseBodyAsString());
            throw new IllegalStateException(
                    "Could not fetch verification status (" + e.getStatusCode().value() + ")");
        }
        if (body == null || body.get("status") == null) {
            throw new IllegalStateException("Didit returned an unexpected decision payload");
        }
        String status = body.get("status").toString();
        String environment = str(body.get("environment"));

        // V3 returns one entry per workflow node; this workflow scans a single
        // document, so the first entry is the one we want.
        Map<String, Object> idv = firstEntry(body.get("id_verifications"));
        if (idv == null) {
            return new DiditDecision(status, null, null, null, null, List.of(), environment);
        }
        return new DiditDecision(
                status,
                str(idv.get("gender")),
                str(idv.get("document_number")),
                str(idv.get("full_name")),
                str(idv.get("date_of_birth")),
                warningRisks(idv.get("warnings")),
                environment);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> firstEntry(Object array) {
        if (array instanceof List<?> list && !list.isEmpty()
                && list.get(0) instanceof Map<?, ?> first) {
            return (Map<String, Object>) first;
        }
        return null;
    }

    /** Pulls the stable {@code risk} codes out of a warnings array. */
    private List<String> warningRisks(Object warnings) {
        if (!(warnings instanceof List<?> list)) {
            return List.of();
        }
        List<String> risks = new ArrayList<>();
        for (Object w : list) {
            if (w instanceof Map<?, ?> map && map.get("risk") != null) {
                risks.add(map.get("risk").toString());
            }
        }
        return risks;
    }

    private String str(Object value) {
        if (value == null) {
            return null;
        }
        String s = value.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private void requireConfigured() {
        if (apiKey == null || apiKey.isBlank() || workflowId == null || workflowId.isBlank()) {
            throw new IllegalStateException(
                    "Identity verification is not configured — set DIDIT_API_KEY (and didit.workflow-id) on the backend");
        }
    }
}
