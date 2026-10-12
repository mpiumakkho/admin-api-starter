package com.adminstarter.accesscontrol.notification.service;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * The emails user management sends, each in the language of the person it goes to (P-029). Implementations send in the
 * background and never fail the caller. The text is the messages of i18n/mail*.properties, so another channel, such as
 * LINE, can say the same things.
 */
public interface NotificationService {

    void sendInvitation(Recipient to, String link, Instant expiresAt);

    void sendPasswordReset(Recipient to, String link, Instant expiresAt);

    /**
     * Tells someone what happened to their account (P-027), or that an access review waits for them (P-028).
     * {@code kind} names the messages: mail.<kind>.subject, mail.<kind>.body (one paragraph per line) and, when it has
     * one, mail.<kind>.footer. The product's name is {0} in each, and {@code values} follow from {1}: a {@link Text} is
     * written in the reader's language too, and an Instant as a date and time there.
     */
    void sendNotice(Recipient to, String kind, List<Object> values);

    /** Who an email goes to: their address, the name it greets them by, and the language it is written in */
    record Recipient(String email, String firstName, Locale locale) {}

    /** A value written in the reader's language: another message, with values of its own (from {1}, as a notice's) */
    record Text(String key, List<Object> values) {

        public static Text of(String key, Object... values) {
            return new Text(key, List.of(values));
        }
    }
}
