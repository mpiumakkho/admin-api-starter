package com.adminstarter.accesscontrol.common.context;

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
 */
public record Actor(UUID userId, UUID sessionId, Set<UUID> roleIds, Set<String> permissions,
        Map<UUID, Set<String>> branchPermissions, boolean twoFactorSetupRequired) {

    public Actor {
        roleIds = Set.copyOf(roleIds);
        permissions = Set.copyOf(permissions);
        branchPermissions = branchPermissions.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> Set.copyOf(entry.getValue())));
    }

    /** Someone with no role limited to a branch */
    public Actor(UUID userId, UUID sessionId, Set<UUID> roleIds, Set<String> permissions, boolean twoFactorSetupRequired) {
        this(userId, sessionId, roleIds, permissions, Map.of(), twoFactorSetupRequired);
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
}
