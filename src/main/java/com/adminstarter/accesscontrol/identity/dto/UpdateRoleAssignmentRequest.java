package com.adminstarter.accesscontrol.identity.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * RoleAssignmentChanges: a field left out stays as it is. {@code endsAt} must tell the two apart, so it is read through
 * a setter that notes it was sent: left out, {@link #endsAt()} is null; {@code "endsAt": null} (for good) is an empty
 * Optional. A record cannot, as Jackson gives a left-out Optional the empty value too.
 */
public final class UpdateRoleAssignmentRequest {

    private boolean endsAtSent;
    private @Nullable Instant endsAt;

    @Size(max = RoleGrantRequest.MAX_REASON)
    private @Nullable String reason;

    @JsonSetter("endsAt")
    void readEndsAt(@Nullable Instant value) {
        endsAtSent = true;
        endsAt = value;
    }

    @JsonSetter("reason")
    void readReason(@Nullable String value) {
        reason = value;
    }

    /** Null when left out; empty for good; or the new end */
    public @Nullable Optional<Instant> endsAt() {
        return endsAtSent ? Optional.ofNullable(endsAt) : null;
    }

    public @Nullable String reason() {
        return reason;
    }
}
