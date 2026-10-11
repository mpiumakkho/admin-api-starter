package com.adminstarter.accesscontrol.identity.event;

import java.util.UUID;

/** A wrong password or code, one too many in a row, locked the account for this long (P-026) */
public record AccountLocked(UUID userId, String fullName, long minutes) {}
