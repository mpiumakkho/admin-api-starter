package com.adminstarter.accesscontrol.identity.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * RoleGrant: a role to give someone. {@code startsAt} left out, or earlier than now, means now; {@code endsAt} null
 * means for good.
 */
public record RoleGrantRequest(
        @NotNull UUID roleId,
        @Nullable Instant startsAt,
        @Nullable Instant endsAt,
        @Nullable @Size(max = MAX_REASON) String reason) {

    public static final int MAX_REASON = 500;
}
