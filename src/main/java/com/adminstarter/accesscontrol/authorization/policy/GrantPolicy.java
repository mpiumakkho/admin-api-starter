package com.adminstarter.accesscontrol.authorization.policy;

import java.util.Collection;
import java.util.Set;

/**
 * Nobody gives out more than they have (API.md, rules 2 and 3; access-rules.ts in admin-ng-starter):
 *
 * <ul>
 *   <li>a role can be assigned, and a permission ticked or unticked on a role, only by someone who holds every
 *       permission involved;
 *   <li>so people can only manage those whose role they could assign, and roles that allow no more than their own:
 *       nobody below an administrator can take over an administrator's account.
 * </ul>
 */
public final class GrantPolicy {

    private GrantPolicy() {}

    /** Whether someone holding {@code held} may give out every permission in {@code permissions} */
    public static boolean holdsAll(Set<String> held, Collection<String> permissions) {
        return held.containsAll(permissions);
    }

    /** Whether someone holding {@code held} may manage a role with these permissions, or a person who has it */
    public static boolean canManageRole(Set<String> held, Collection<String> rolePermissions) {
        return holdsAll(held, rolePermissions);
    }
}
