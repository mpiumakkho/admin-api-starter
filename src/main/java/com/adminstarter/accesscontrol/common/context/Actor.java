package com.adminstarter.accesscontrol.common.context;

import com.adminstarter.accesscontrol.common.constant.ErrorCode;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * Who is making the request: the signed-in user, the session their access token belongs to, the roles they hold right
 * now and everything those roles allow, together (P-019). PermissionAuthenticationConverter builds it from the token;
 * controllers receive it with {@code @AuthenticationPrincipal} and hand it to the services, which never read it from
 * a thread.
 *
 * @param roleIds the roles they hold now, for the whole organization or for a branch: they change none of these
 * @param permissions what the roles they hold for the whole organization allow: what user management checks
 *     ({@code hasAuthority}) and the app's modules check for records of no branch
 * @param branchPermissions what roles limited to a branch allow there, by branch id (P-020)
 * @param twoFactorSetupRequired one of their roles requires two-step sign-in and they have not turned it on: until they
 *     do, only setting it up is allowed (P-016)
 * @param recentSignIn they signed in, or confirmed their password, within the settings' reauthMinutes, or those are 0:
 *     the risky changes go through (P-023)
 */
public record Actor(UUID userId, UUID sessionId, Set<UUID> roleIds, Set<String> permissions,
        Map<UUID, Set<String>> branchPermissions, boolean twoFactorSetupRequired, boolean recentSignIn) {

    public Actor {
        roleIds = Set.copyOf(roleIds);
        permissions = Set.copyOf(permissions);
        branchPermissions = branchPermissions.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> Set.copyOf(entry.getValue())));
    }

    /** Someone with no role limited to a branch, who has just signed in */
    public Actor(UUID userId, UUID sessionId, Set<UUID> roleIds, Set<String> permissions, boolean twoFactorSetupRequired) {
        this(userId, sessionId, roleIds, permissions, Map.of(), twoFactorSetupRequired, true);
    }

    /** Across the whole organization */
    public boolean has(String permission) {
        return permissions.contains(permission);
    }

    /**
     * Across the whole organization or, for a record of a branch, there too through a role limited to it: how the app's
     * modules check a record that belongs to a branch, such as an order. Null is a record of no branch.
     */
    public boolean can(String permission, @Nullable UUID branchId) {
        return has(permission) || (branchId != null && branchPermissions.getOrDefault(branchId, Set.of()).contains(permission));
    }

    public boolean is(UUID userId) {
        return this.userId.equals(userId);
    }

    /**
     * What their roles allow in a scope: for the whole organization (null), their permissions there; in a branch, those
     * together with what their roles limited to it allow. Giving a role and managing its holders count in the role's
     * scope (P-024).
     */
    public Set<String> permissionsIn(@Nullable UUID branchId) {
        var limited = branchId == null ? Set.<String>of() : branchPermissions.getOrDefault(branchId, Set.of());
        if (limited.isEmpty()) {
            return permissions;
        }
        var all = new HashSet<>(permissions);
        all.addAll(limited);
        return Set.copyOf(all);
    }

    /**
     * For the changes that could take the system over or shut people out (API.md, "Confirming who you are"):
     * reauth_required unless they signed in or confirmed their password recently enough. Asked once the change is known
     * to be allowed, before anything changes.
     */
    public void requireRecentSignIn() {
        if (!recentSignIn) {
            throw ErrorCode.REAUTH_REQUIRED.exception();
        }
    }
}
