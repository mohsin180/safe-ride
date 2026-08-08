package com.saferide.monolith.user.services.mail;

/**
 * How an email actually leaves the app. Kept separate from the message
 * templates so switching provider is a config change, not a rewrite of every
 * place that composes a mail.
 */
public interface MailTransport {

    /**
     * Delivers one HTML message. Implementations must not throw on a delivery
     * failure — mail is sent asynchronously and a bounce should never break
     * the request that triggered it. Log and return instead.
     */
    void send(String toEmail, String subject, String htmlBody);
}
