package com.adminstarter.accesscontrol.common.context;

import java.util.Set;
import java.util.UUID;

/**
 * Who is making the request: the signed-in user, the session their access token belongs to, and what their role
 * allows right now. PermissionAuthenticationConverter builds it from the token; controllers receive it with
 * {@code @AuthenticationPrincipal} and hand it to the services, which never read it from a thread.
 */
public record Actor(UUID userId, UUID sessionId, UUID roleId, Set<String> permissions) {

    public Actor {
        permissions = Set.copyOf(permissions);
    }

    public boolean has(String permission) {
        return permissions.contains(permission);
    }

    public boolean is(UUID userId) {
        return this.userId.equals(userId);
    }
}
