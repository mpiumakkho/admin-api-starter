package com.adminstarter.accesscontrol.identity.dto;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * What sign-in and the per-request check need to know about an account. It keeps every role given, with its dates,
 * so a cached copy still knows the moment one starts or ends.
 */
public record AccountSummary(UUID id, String email, String firstName, String fullName, String status, List<Grant> roles,
        boolean twoFactor) {

    public AccountSummary {
        roles = List.copyOf(roles);
    }

    public boolean active() {
        return "active".equals(status);
    }

    /** The roles held at this moment */
    public Set<UUID> roleIdsAt(Instant now) {
        return roles.stream().filter(grant -> grant.activeAt(now)).map(Grant::roleId).collect(Collectors.toUnmodifiableSet());
    }

    /** A role given from a start until an end, or for good */
    public record Grant(UUID roleId, Instant startsAt, @Nullable Instant endsAt) {

        boolean activeAt(Instant now) {
            return !startsAt.isAfter(now) && (endsAt == null || endsAt.isAfter(now));
        }
    }
}
