package com.adminstarter.accesscontrol.notification.service;

import java.time.Instant;

/** The emails user management sends. Implementations send in the background and never fail the caller. */
public interface NotificationService {

    void sendInvitation(String email, String firstName, String link, Instant expiresAt);

    void sendPasswordReset(String email, String firstName, String link, Instant expiresAt);
}
