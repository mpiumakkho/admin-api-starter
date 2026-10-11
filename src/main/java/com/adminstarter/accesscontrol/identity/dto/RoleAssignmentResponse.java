package com.adminstarter.accesscontrol.identity.dto;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** RoleAssignment in admin-ng-starter's models.ts; branchId, endsAt and assignedById are null rather than left out */
public record RoleAssignmentResponse(
        UUID id,
        UUID userId,
        UUID roleId,
        @Nullable UUID branchId,
        Instant startsAt,
        @Nullable Instant endsAt,
        String reason,
        @Nullable UUID assignedById,
        Instant assignedAt) {}
