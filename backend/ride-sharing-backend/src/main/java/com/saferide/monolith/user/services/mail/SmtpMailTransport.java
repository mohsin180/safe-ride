package com.saferide.monolith.user.services.mail;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * Plain SMTP delivery, kept for local runs and for Brevo's SMTP relay (set
 * {@code MAIL_PROVIDER=smtp} plus the {@code spring.mail.*} host/credentials).
 * Brevo's HTTP API is the default because it needs no outbound SMTP port.
 */
@Component
@ConditionalOnProperty(name = "mail.provider", havingValue = "smtp")
public class SmtpMailTransport implements MailTransport {

    private static final Logger log = LoggerFactory.getLogger(SmtpMailTransport.class);

    private final JavaMailSender mailSender;
    private final String fromEmail;
    private final String fromName;

    public SmtpMailTransport(JavaMailSender mailSender,
                             @Value("${mail.from-email:}") String fromEmail,
                             @Value("${mail.from-name:SafeRide}") String fromName) {
        this.mailSender = mailSender;
        this.fromEmail = fromEmail;
        this.fromName = fromName;
    }

    @Override
    public void send(String toEmail, String subject, String htmlBody) {
        if (fromEmail == null || fromEmail.isBlank()) {
            log.error("SMTP is not configured — set MAIL_FROM_EMAIL. Dropping '{}' to {}",
                    subject, toEmail);
            return;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail, fromName);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(message);
            log.info("Sent '{}' to {} via SMTP", subject, toEmail);
        } catch (MessagingException | java.io.UnsupportedEncodingException e) {
            log.error("Could not send '{}' to {}: {}", subject, toEmail, e.getMessage());
        }
    }
}
