package com.adminstarter.accesscontrol.authorization.service;

import com.adminstarter.accesscontrol.authorization.dto.RoleResponse;
import com.adminstarter.accesscontrol.common.context.Actor;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** What other modules may ask about roles */
public interface RoleService {

    /** The permission codes a role grants; none when the role does not exist. Cached, evicted when roles change. */
    Set<String> permissionsOf(UUID roleId);

    Optional<RoleResponse> find(UUID roleId);

    Optional<UUID> findIdByCode(String code);

    /** Refuses with not_found when the role does not exist, forbidden when it grants a permission the actor lacks */
    void requireAssignable(Actor actor, UUID roleId);

    /** Whether the actor may manage someone holding this role: edit, suspend, delete, reset, invite again */
    boolean canManageHolderOf(Actor actor, UUID roleId);
}
