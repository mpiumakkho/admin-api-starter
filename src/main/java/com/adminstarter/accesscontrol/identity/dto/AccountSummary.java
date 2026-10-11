package com.adminstarter.accesscontrol.identity.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    /** The roles held at this moment, for the whole organization or for a branch */
    public Set<UUID> roleIdsAt(Instant now) {
        return roles.stream().filter(grant -> grant.activeAt(now)).map(Grant::roleId).collect(Collectors.toUnmodifiableSet());
    }

    /** The roles held at this moment for the whole organization: user management's permissions come from these */
    public Set<UUID> organizationRoleIdsAt(Instant now) {
        return roles.stream().filter(grant -> grant.branchId() == null && grant.activeAt(now)).map(Grant::roleId)
                .collect(Collectors.toUnmodifiableSet());
    }

    /** The roles held at this moment for one branch only, by branch id (P-020) */
    public Map<UUID, Set<UUID>> branchRoleIdsAt(Instant now) {
        return roles.stream().filter(grant -> grant.branchId() != null && grant.activeAt(now))
                .collect(Collectors.groupingBy(grant -> Objects.requireNonNull(grant.branchId()),
                        Collectors.mapping(Grant::roleId, Collectors.toUnmodifiableSet())));
    }

    /** A role given from a start until an end, or for good, for the whole organization (branchId null) or a branch */
    public record Grant(UUID roleId, @Nullable UUID branchId, Instant startsAt, @Nullable Instant endsAt) {

        boolean activeAt(Instant now) {
            return !startsAt.isAfter(now) && (endsAt == null || endsAt.isAfter(now));
        }
    }
}
