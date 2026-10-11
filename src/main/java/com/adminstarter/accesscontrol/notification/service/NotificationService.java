package com.adminstarter.accesscontrol.notification.service;

import java.time.Instant;
import java.util.List;

/** The emails user management sends. Implementations send in the background and never fail the caller. */
public interface NotificationService {

    void sendInvitation(String email, String firstName, String link, Instant expiresAt);

    void sendPasswordReset(String email, String firstName, String link, Instant expiresAt);

    /**
     * Tells someone what happened to their account (P-027): a sign-in from a new device, a role given, their email or
     * password changed, the account suspended. Each line is a paragraph.
     */
    void sendNotice(String email, String firstName, String subject, List<String> lines);
}
