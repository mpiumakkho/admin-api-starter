package com.adminstarter.accesscontrol.authorization.service;

import static com.adminstarter.accesscontrol.common.constant.ErrorCode.FORBIDDEN;
import static com.adminstarter.accesscontrol.common.constant.ErrorCode.NOT_FOUND;

import com.adminstarter.accesscontrol.authorization.dto.RoleResponse;
import com.adminstarter.accesscontrol.authorization.entity.Role;
import com.adminstarter.accesscontrol.authorization.policy.GrantPolicy;
import com.adminstarter.accesscontrol.authorization.repository.RoleRepository;
import com.adminstarter.accesscontrol.common.context.Actor;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class RoleServiceImpl implements RoleService {

    private final RoleRepository roles;
    private final RoleMapper mapper;

    public RoleServiceImpl(RoleRepository roles, RoleMapper mapper) {
        this.roles = roles;
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
    public void requireAssignable(Actor actor, UUID roleId) {
        var role = roles.findById(roleId).orElseThrow(NOT_FOUND::exception);
        if (!GrantPolicy.holdsAll(actor.permissions(), role.getPermissions())) {
            throw FORBIDDEN.exception();
        }
    }

    @Override
    public boolean canManageHolderOf(Actor actor, UUID roleId) {
        return GrantPolicy.canManageRole(actor.permissions(), roles.findById(roleId).map(Role::getPermissions).orElse(Set.of()));
    }
}
