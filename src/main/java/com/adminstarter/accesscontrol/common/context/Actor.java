package com.adminstarter.accesscontrol.common.context;

import java.util.Set;
import java.util.UUID;

/**
 * Who is making the request: the signed-in user, the session their access token belongs to, the roles they hold right
 * now and everything those roles allow, together (P-019). PermissionAuthenticationConverter builds it from the token;
 * controllers receive it with {@code @AuthenticationPrincipal} and hand it to the services, which never read it from
 * a thread.
 *
 * @param twoFactorSetupRequired one of their roles requires two-step sign-in and they have not turned it on: until they
 *     do, only setting it up is allowed (P-016)
 */
public record Actor(UUID userId, UUID sessionId, Set<UUID> roleIds, Set<String> permissions, boolean twoFactorSetupRequired) {

    public Actor {
        roleIds = Set.copyOf(roleIds);
        permissions = Set.copyOf(permissions);
    }

    public boolean has(String permission) {
        return permissions.contains(permission);
    }

    public boolean is(UUID userId) {
        return this.userId.equals(userId);
    }
}
