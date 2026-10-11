package com.adminstarter.accesscontrol.identity.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * POST /role-assignments/batch: a role for several people at once ({@code add}), or taken away from them, with a reason
 * (P-021); a role given carries its own reason in the grant
 */
public record RoleAssignmentBatchRequest(
        @NotNull @Size(max = 1000) List<@NotNull UUID> userIds,
        @Nullable @Valid RoleGrantRequest add,
        @Nullable UUID removeRoleId,
        @Nullable @Size(max = RoleGrantRequest.MAX_REASON) String reason) {}
