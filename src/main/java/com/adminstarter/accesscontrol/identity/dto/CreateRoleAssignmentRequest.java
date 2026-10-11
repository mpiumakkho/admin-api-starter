package com.adminstarter.accesscontrol.identity.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** RoleAssignmentDraft: POST /role-assignments gives one person a role */
public record CreateRoleAssignmentRequest(
        @NotNull UUID userId,
        @NotNull UUID roleId,
        @Nullable UUID branchId,
        @Nullable Instant startsAt,
        @Nullable Instant endsAt,
        @Nullable @Size(max = RoleGrantRequest.MAX_REASON) String reason) {

    public RoleGrantRequest grant() {
        return new RoleGrantRequest(roleId, branchId, startsAt, endsAt, reason);
    }
}
