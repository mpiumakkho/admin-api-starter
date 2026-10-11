package com.adminstarter.accesscontrol.identity.event;

import java.time.Instant;
import java.util.UUID;

/** A role given for a time reached its end; nobody did it, so the activity log has no actor (P-019) */
public record RoleAssignmentExpired(UUID userId, String fullName, UUID roleId, Instant endedAt) {}
