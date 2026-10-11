package com.adminstarter.accesscontrol.identity.event;

import java.util.UUID;

/** Someone used a recovery code instead of a code from the app (P-025) */
public record RecoveryCodeUsed(UUID userId, String fullName, int remaining) {}
