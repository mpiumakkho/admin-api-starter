package com.adminstarter.accesscontrol.identity.dto;

import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/** The body of a change that takes only a reason, such as resetting someone's password (P-021); it may be left out */
public record ReasonRequest(@Nullable @Size(max = RoleGrantRequest.MAX_REASON) String reason) {

    /** The reason of a body that may be missing */
    public static @Nullable String of(@Nullable ReasonRequest request) {
        return request == null ? null : request.reason();
    }
}
