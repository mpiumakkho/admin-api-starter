package com.adminstarter.accesscontrol.authorization.service;

import static com.adminstarter.accesscontrol.common.constant.ErrorCode.FORBIDDEN;
import static com.adminstarter.accesscontrol.common.constant.ErrorCode.NOT_FOUND;

import com.adminstarter.accesscontrol.authorization.dto.RoleResponse;
import com.adminstarter.accesscontrol.authorization.entity.Permission;
import com.adminstarter.accesscontrol.authorization.entity.Role;
import com.adminstarter.accesscontrol.authorization.policy.GrantPolicy;
import com.adminstarter.accesscontrol.authorization.repository.PermissionRepository;
import com.adminstarter.accesscontrol.authorization.repository.RoleRepository;
import com.adminstarter.accesscontrol.common.context.Actor;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class RoleServiceImpl implements RoleService {

    private final RoleRepository roles;
    private final PermissionRepository permissions;
    private final RoleMapper mapper;

    public RoleServiceImpl(RoleRepository roles, PermissionRepository permissions, RoleMapper mapper) {
        this.roles = roles;
        this.permissions = permissions;
        this.mapper = mapper;
    }

    @Override
    @Cacheable(cacheNames = RoleMapper.PERMISSIONS_CACHE)
    public Set<String> permissionsOf(UUID roleId) {
        return roles.findById(roleId).map(Role::getPermissions).orElse(Set.of());
    }

    @Override
    public Optional<RoleResponse> find(UUID roleId) {
        return roles.findById(roleId).map(mapper::toResponse);
    }

    @Override
    public Optional<UUID> findIdByCode(String code) {
        return roles.findByCode(code).map(Role::getId);
    }

    @Override
    public RoleResponse requireAssignable(Actor actor, UUID roleId, @Nullable UUID branchId) {
        var role = roles.findById(roleId).orElseThrow(NOT_FOUND::exception);
        if (!GrantPolicy.holdsAll(actor.permissionsIn(branchId), role.getPermissions())) {
            throw FORBIDDEN.exception();
        }
        return mapper.toResponse(role);
    }

    @Override
    public boolean canManageHolderOf(Actor actor, UUID roleId, @Nullable UUID branchId) {
        return GrantPolicy.canManageRole(actor.permissionsIn(branchId), roles.findById(roleId).map(Role::getPermissions).orElse(Set.of()));
    }

    @Override
    public Set<UUID> fullAccessRoleIds() {
        var catalog = permissions.findAll().stream()
                .filter(permission -> !permission.isDeprecated())
                .map(Permission::getCode)
                .collect(Collectors.toSet());
        return roles.findAll().stream()
                .filter(role -> GrantPolicy.holdsAll(role.getPermissions(), catalog))
                .map(Role::getId)
                .collect(Collectors.toUnmodifiableSet());
    }
}
