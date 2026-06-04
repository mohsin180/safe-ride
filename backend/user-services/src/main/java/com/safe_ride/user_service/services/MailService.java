package com.safe_ride.user_service.services;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class MailService {
    private final JavaMailSender mailSender;
    @Value("${spring.mail.username}")
    private String fromEmail;
    @Value("${app.verification.base-url}")
    private String verificationBaseUrl;

    public MailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Async
    public void sendEmail(String email, String rawToken) {

        try {
            String verificationLink =
                    verificationBaseUrl + "/verify-email?token=" + rawToken;
            String htmlBody = buildVerificationEmailBody(verificationLink);
            MimeMessage mailMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    mailMessage, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(email);
            helper.setSubject("Verify Your Account - Safe Ride");
            helper.setText(htmlBody, true);
            mailSender.send(mailMessage);
            log.info("Verification email sent successfully to {}", email);
        } catch (MessagingException e) {
            log.error("Failed to send verification email to {}: {}", email, e.getMessage(), e);
        }

    }

    private String buildVerificationEmailBody(String verificationLink) {
        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                </head>
                <body style="margin:0; padding:0; background-color:#f4f4f7; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;">
                    <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background-color:#f4f4f7; padding: 40px 0;">
                        <tr>
                            <td align="center">
                                <table role="presentation" width="600" cellpadding="0" cellspacing="0" style="background-color:#ffffff; border-radius:8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); overflow:hidden;">
                                    <!-- Header -->
                                    <tr>
                                        <td style="background-color:#1a73e8; padding: 30px 40px; text-align:center;">
                                            <h1 style="color:#ffffff; margin:0; font-size:24px;">SafeRide</h1>
                                        </td>
                                    </tr>
                                    <!-- Body -->
                                    <tr>
                                        <td style="padding: 40px;">
                                            <h2 style="color:#333333; margin-top:0;">Verify Your Email Address</h2>
                                            <p style="color:#555555; font-size:16px; line-height:1.6;">
                                                Thank you for registering with SafeRide! Please click the button below to verify your email address and activate your account.
                                            </p>
                                            <table role="presentation" cellpadding="0" cellspacing="0" style="margin: 30px 0;">
                                                <tr>
                                                    <td style="background-color:#1a73e8; border-radius:6px;">
                                                        <a href="%s" style="display:inline-block; padding: 14px 32px; color:#ffffff; text-decoration:none; font-size:16px; font-weight:600;">
                                                            Verify Email
                                                        </a>
                                                    </td>
                                                </tr>
                                            </table>
                                            <p style="color:#888888; font-size:14px; line-height:1.5;">
                                                This link will expire in <strong>24 hours</strong>. If you did not create an account, you can safely ignore this email.
                                            </p>
                                            <hr style="border:none; border-top:1px solid #eeeeee; margin: 30px 0;">
                                            <p style="color:#aaaaaa; font-size:12px;">
                                                If the button doesn't work, copy and paste this URL into your browser:<br>
                                                <a href="%s" style="color:#1a73e8; word-break:break-all;">%s</a>
                                            </p>
                                        </td>
                                    </tr>
                                    <!-- Footer -->
                                    <tr>
                                        <td style="background-color:#f9f9fb; padding: 20px 40px; text-align:center;">
                                            <p style="color:#aaaaaa; font-size:12px; margin:0;">
                                                &copy; 2026 SafeRide. All rights reserved.
                                            </p>
                                        </td>
                                    </tr>
                                </table>
                            </td>
                        </tr>
                    </table>
                </body>
                </html>
                """
                .formatted(verificationLink, verificationLink, verificationLink);
    }
}
