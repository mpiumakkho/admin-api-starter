package com.adminstarter.accesscontrol.identity.dto;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * One role held, now or later, by one person through one assignment, as an access review keeps it (P-028): the person's
 * name and email as they were, and the assignment's scope and dates
 */
public record RoleHolderSummary(UUID assignmentId, UUID userId, String fullName, String email, UUID roleId,
        @Nullable UUID branchId, Instant startsAt, @Nullable Instant endsAt) {}
