package com.adminstarter.accesscontrol.identity.event;

import java.util.UUID;

/** The server suspended an account nobody used for the settings' suspendInactiveDays (P-027) */
public record UserInactiveSuspended(UUID userId, String fullName, String email, String firstName, int days) {}
