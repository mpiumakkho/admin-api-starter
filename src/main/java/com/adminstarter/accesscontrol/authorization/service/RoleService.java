package com.adminstarter.accesscontrol.authorization.service;

import com.adminstarter.accesscontrol.authorization.dto.RoleResponse;
import com.adminstarter.accesscontrol.common.context.Actor;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** What other modules may ask about roles */
public interface RoleService {

    /** The permission codes a role grants; none when the role does not exist. Cached, evicted when roles change. */
    Set<String> permissionsOf(UUID roleId);

    Optional<RoleResponse> find(UUID roleId);

    Optional<UUID> findIdByCode(String code);

    /**
     * The role, with its rules for giving it, for the whole organization (branchId null) or limited to a branch; refuses
     * with not_found when it does not exist, forbidden when it grants a permission the actor lacks in that scope
     * (P-024)
     */
    RoleResponse requireAssignable(Actor actor, UUID roleId, @Nullable UUID branchId);

    /**
     * Whether the actor may manage someone holding this role for the whole organization (branchId null) or limited to
     * a branch: they have its every permission in that scope (P-024)
     */
    boolean canManageHolderOf(Actor actor, UUID roleId, @Nullable UUID branchId);

    /** The roles that grant every permission there is, such as the built-in Administrator: someone must keep one */
    Set<UUID> fullAccessRoleIds();
}
