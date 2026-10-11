package com.adminstarter.accesscontrol.identity.policy;

import com.adminstarter.accesscontrol.authorization.CorePermissions;
import com.adminstarter.accesscontrol.common.context.Actor;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * Where someone may use user management's permissions (API.md, "Branch administrators"; P-024): across the whole
 * organization through their roles for it, or, for the four of CorePermissions.BRANCH_SCOPED, over the people of a
 * branch through a role limited to it.
 */
public final class BranchScopePolicy {

    private BranchScopePolicy() {}

    /**
     * Over a person who belongs to this branch, or to none (null), or for an assignment in that scope: for the whole
     * organization, or, for a branch-scoped permission, through a role limited to that branch
     */
    public static boolean allows(Actor actor, String permission, @Nullable UUID branchId) {
        return actor.has(permission) || (branchId != null && CorePermissions.BRANCH_SCOPED.contains(permission)
                && actor.branchPermissions().getOrDefault(branchId, Set.of()).contains(permission));
    }

    /** The branches where they have a branch-scoped permission through a role limited to them */
    public static Set<UUID> branchesWith(Actor actor, String permission) {
        if (!CorePermissions.BRANCH_SCOPED.contains(permission)) {
            return Set.of();
        }
        return actor.branchPermissions().entrySet().stream()
                .filter(entry -> entry.getValue().contains(permission))
                .map(Map.Entry::getKey)
                .collect(Collectors.toUnmodifiableSet());
    }
}
