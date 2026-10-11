package com.adminstarter.accesscontrol.identity.event;

import java.util.UUID;

/**
 * Someone with users.edit reset a user's password: the user gets an email link to set a new one. For someone else,
 * the old password has stopped working and their sessions end; resetting your own keeps both.
 */
public record UserPasswordReset(UUID actorId, UUID userId, String fullName, String email, String firstName, boolean self,
        String reason) {}
