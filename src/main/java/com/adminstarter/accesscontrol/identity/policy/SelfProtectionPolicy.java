package com.adminstarter.accesscontrol.identity.policy;

import static com.adminstarter.accesscontrol.common.constant.ErrorCode.FORBIDDEN;
import static com.adminstarter.accesscontrol.common.constant.ErrorCode.SELF_CHANGE;

import com.adminstarter.accesscontrol.common.context.Actor;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/**
 * What people may and may not do to their own account (API.md, rule 4; access-rules.ts in admin-ng-starter):
 *
 * <ul>
 *   <li>nobody suspends or deletes themselves or changes their own role, so an administrator cannot lock everyone
 *       out by accident (the role side, changing your own role's permissions, is in RoleManagementService);
 *   <li>everyone may change their own name, phone and photo without users.edit.
 * </ul>
 */
public final class SelfProtectionPolicy {

    /** What people can change on their own account without users.edit (SELF_EDITABLE_FIELDS) */
    public static final Set<String> SELF_EDITABLE_FIELDS = Set.of("firstName", "lastName", "phone", "avatar");

    private SelfProtectionPolicy() {}

    /** Suspending, activating or deleting a list that includes yourself */
    public static void forbidSelf(Actor actor, Collection<UUID> userIds) {
        if (userIds.contains(actor.userId())) {
            throw SELF_CHANGE.exception();
        }
    }

    public static void forbidOwnRoleOrStatus(Actor actor, UUID userId, boolean roleChanged, boolean statusChanged) {
        if (actor.is(userId) && (roleChanged || statusChanged)) {
            throw SELF_CHANGE.exception();
        }
    }

    /** Without users.edit, only your own name, phone and photo */
    public static void requireSelfEditable(Actor actor, UUID userId, Collection<String> changedFields) {
        if (!actor.is(userId) || !SELF_EDITABLE_FIELDS.containsAll(changedFields)) {
            throw FORBIDDEN.exception();
        }
    }
}
