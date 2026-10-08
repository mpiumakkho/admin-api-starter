package com.adminstarter.accesscontrol.authorization.service;

import static com.adminstarter.accesscontrol.common.constant.ErrorCode.FORBIDDEN;
import static com.adminstarter.accesscontrol.common.constant.ErrorCode.INVALID_REQUEST;
import static com.adminstarter.accesscontrol.common.constant.ErrorCode.NOT_FOUND;
import static com.adminstarter.accesscontrol.common.constant.ErrorCode.SELF_CHANGE;
import static com.adminstarter.accesscontrol.common.constant.ErrorCode.SYSTEM_ROLE;

import com.adminstarter.accesscontrol.authorization.dto.CreateRoleRequest;
import com.adminstarter.accesscontrol.authorization.dto.RoleResponse;
import com.adminstarter.accesscontrol.authorization.dto.UpdateRoleRequest;
import com.adminstarter.accesscontrol.authorization.entity.Permission;
import com.adminstarter.accesscontrol.authorization.entity.Role;
import com.adminstarter.accesscontrol.authorization.event.RoleCreated;
import com.adminstarter.accesscontrol.authorization.event.RoleDeleted;
import com.adminstarter.accesscontrol.authorization.event.RoleUpdated;
import com.adminstarter.accesscontrol.authorization.policy.GrantPolicy;
import com.adminstarter.accesscontrol.authorization.repository.PermissionRepository;
import com.adminstarter.accesscontrol.authorization.repository.RoleRepository;
import com.adminstarter.accesscontrol.common.context.Actor;
import com.adminstarter.accesscontrol.common.dto.FieldChange;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creating, changing and deleting roles from the dashboard (roles.manage), with the rules in API.md */
@Service
@Transactional
public class RoleManagementService {

    private static final String DEFAULT_TONE = "neutral";

    private final RoleRepository roles;
    private final PermissionRepository permissions;
    private final RoleMapper mapper;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public RoleManagementService(RoleRepository roles, PermissionRepository permissions, RoleMapper mapper,
            ApplicationEventPublisher events, Clock clock) {
        this.roles = roles;
        this.permissions = permissions;
        this.mapper = mapper;
        this.events = events;
        this.clock = clock;
    }

    /** Everyone signed in reads roles, to show role names */
    @Transactional(readOnly = true)
    public List<RoleResponse> list() {
        return mapper.toResponses(roles.findAllByOrderByIdAsc());
    }

    public RoleResponse create(Actor actor, CreateRoleRequest request) {
        var granted = known(Objects.requireNonNullElse(request.permissions(), List.of()));
        // Nobody gives out more than they have
        if (!GrantPolicy.holdsAll(actor.permissions(), granted)) {
            throw FORBIDDEN.exception();
        }
        var role = roles.save(new Role(null, request.name(), Objects.requireNonNullElse(request.description(), ""),
                Objects.requireNonNullElse(request.tone(), DEFAULT_TONE), false, granted, now()));
        var order = mapper.order();
        events.publishEvent(new RoleCreated(actor.userId(), role.getId(), role.getName(),
                List.of(FieldChange.list("permissions", RoleMapper.ordered(granted, order), List.of()))));
        return mapper.toResponse(role);
    }

    @CacheEvict(cacheNames = RoleMapper.PERMISSIONS_CACHE, allEntries = true)
    public RoleResponse update(Actor actor, UUID id, UpdateRoleRequest request) {
        var role = roles.findById(id).orElseThrow(NOT_FOUND::exception);
        // Nobody changes a role that allows more than their own
        if (!GrantPolicy.canManageRole(actor.permissions(), role.getPermissions())) {
            throw FORBIDDEN.exception();
        }
        var granted = request.permissions() == null ? role.getPermissions() : known(request.permissions());
        var added = difference(granted, role.getPermissions());
        var removed = difference(role.getPermissions(), granted);
        if (!added.isEmpty() || !removed.isEmpty()) {
            if (id.equals(actor.roleId())) {
                throw SELF_CHANGE.exception();
            }
            if (!GrantPolicy.holdsAll(actor.permissions(), union(added, removed))) {
                throw FORBIDDEN.exception();
            }
        }
        var name = Objects.requireNonNullElse(request.name(), role.getName());
        var description = Objects.requireNonNullElse(request.description(), role.getDescription());
        var tone = Objects.requireNonNullElse(request.tone(), role.getTone());
        var changes = new ArrayList<FieldChange>();
        addIfChanged(changes, "name", role.getName(), name);
        addIfChanged(changes, "description", role.getDescription(), description);
        addIfChanged(changes, "tone", role.getTone(), tone);
        if (!added.isEmpty() || !removed.isEmpty()) {
            var order = mapper.order();
            changes.add(FieldChange.list("permissions", RoleMapper.ordered(added, order), RoleMapper.ordered(removed, order)));
        }
        role.update(name, description, tone, granted, now());
        events.publishEvent(new RoleUpdated(actor.userId(), id, name, List.copyOf(changes)));
        return mapper.toResponse(role);
    }

    @CacheEvict(cacheNames = RoleMapper.PERMISSIONS_CACHE, allEntries = true)
    public void delete(Actor actor, UUID id) {
        var role = roles.findById(id).orElseThrow(NOT_FOUND::exception);
        if (!GrantPolicy.canManageRole(actor.permissions(), role.getPermissions())) {
            throw FORBIDDEN.exception();
        }
        if (role.isSystem()) {
            throw SYSTEM_ROLE.exception();
        }
        var removed = RoleMapper.ordered(role.getPermissions(), mapper.order());
        events.publishEvent(new RoleDeleted(actor.userId(), id, role.getName(),
                List.of(FieldChange.list("permissions", List.of(), removed))));
        roles.delete(role);
        // Users holding the role, or the settings giving it to new users, still point at it: the foreign keys
        // fk_users_role and fk_admin_settings_default_role refuse the delete, and ApiExceptionHandler answers
        // role_in_use or default_role. Flushing here makes that happen inside this transaction.
        roles.flush();
    }

    /** The codes, checked against the catalog; an unknown or retired one is invalid_request */
    private Set<String> known(List<String> codes) {
        var catalog = permissions.findAll().stream()
                .filter(permission -> !permission.isDeprecated())
                .map(Permission::getCode)
                .collect(Collectors.toSet());
        if (!catalog.containsAll(codes)) {
            throw INVALID_REQUEST.exception();
        }
        return Set.copyOf(codes);
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }

    private static void addIfChanged(List<FieldChange> changes, String field, String from, String to) {
        if (!from.equals(to)) {
            changes.add(FieldChange.of(field, from, to));
        }
    }

    private static Set<String> difference(Set<String> left, Set<String> right) {
        var result = new HashSet<>(left);
        result.removeAll(right);
        return result;
    }

    private static Set<String> union(Set<String> left, Set<String> right) {
        var result = new HashSet<>(left);
        result.addAll(right);
        return result;
    }
}
