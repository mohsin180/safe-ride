package com.saferide.monolith.user.services.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

/**
 * Sends through Brevo's transactional email API
 * ({@code POST /v3/smtp/email}, authenticated with an {@code api-key} header).
 *
 * <p>Chosen over Brevo's SMTP relay because it needs no outbound SMTP port —
 * which many hosts block — and because a rejected send comes back as a
 * readable JSON error instead of an opaque SMTP failure.
 */
@Component
@ConditionalOnProperty(name = "mail.provider", havingValue = "brevo", matchIfMissing = true)
public class BrevoMailTransport implements MailTransport {

    private static final Logger log = LoggerFactory.getLogger(BrevoMailTransport.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RestClient client;
    private final String apiKey;
    private final String fromEmail;
    private final String fromName;

    public BrevoMailTransport(
            @Value("${mail.brevo.api-key:}") String apiKey,
            @Value("${mail.brevo.base-url:https://api.brevo.com}") String baseUrl,
            @Value("${mail.from-email:}") String fromEmail,
            @Value("${mail.from-name:SafeRide}") String fromName) {
        this.apiKey = apiKey;
        this.fromEmail = fromEmail;
        this.fromName = fromName;
        this.client = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("api-key", apiKey)
                .defaultHeader("accept", MediaType.APPLICATION_JSON_VALUE)
                // Registered explicitly: the bare builder's defaults can't read
                // Brevo's JSON replies. Slotting it in as the JSON converter
                // (rather than first in the list) leaves the byte[] converter
                // to handle the pre-serialised body below, which otherwise
                // gets JSON-encoded a second time.
                .configureMessageConverters(c -> c
                        .registerDefaults()
                        .withJsonConverter(new JacksonJsonHttpMessageConverter()))
                .build();
    }

    @Override
    public void send(String toEmail, String subject, String htmlBody) {
        if (apiKey == null || apiKey.isBlank() || fromEmail == null || fromEmail.isBlank()) {
            log.error("Brevo is not configured — set BREVO_API_KEY and MAIL_FROM_EMAIL. "
                    + "Dropping '{}' to {}", subject, toEmail);
            return;
        }
        try {
            // Serialised to bytes so the request carries a Content-Length
            // rather than being streamed chunked, which some API gateways
            // read as an empty body.
            byte[] payload = MAPPER.writeValueAsBytes(Map.of(
                    "sender", Map.of("email", fromEmail, "name", fromName),
                    "to", List.of(Map.of("email", toEmail)),
                    "subject", subject,
                    "htmlContent", htmlBody));

            client.post()
                    .uri("/v3/smtp/email")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();

            log.info("Sent '{}' to {} via Brevo", subject, toEmail);
        } catch (RestClientResponseException e) {
            // 401 => bad key. 400 with "sender not valid" => the from-address
            // isn't verified in the Brevo account yet.
            log.error("Brevo rejected '{}' to {}: {} {}",
                    subject, toEmail, e.getStatusCode(), e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("Could not send '{}' to {}: {}", subject, toEmail, e.getMessage());
        }
    }
}
