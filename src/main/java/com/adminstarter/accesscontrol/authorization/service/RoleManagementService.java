package com.adminstarter.accesscontrol.authorization.service;

import static com.adminstarter.accesscontrol.common.constant.ErrorCode.FORBIDDEN;
import static com.adminstarter.accesscontrol.common.constant.ErrorCode.INVALID_REQUEST;
import static com.adminstarter.accesscontrol.common.constant.ErrorCode.NOT_FOUND;
import static com.adminstarter.accesscontrol.common.constant.ErrorCode.ROLE_IN_USE;
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
import com.adminstarter.accesscontrol.authorization.spi.RoleHolderProvider;
import com.adminstarter.accesscontrol.common.context.Actor;
import com.adminstarter.accesscontrol.common.dto.FieldChange;
import java.time.Clock;
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
    private final RoleHolderProvider holders;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public RoleManagementService(RoleRepository roles, PermissionRepository permissions, RoleMapper mapper,
            RoleHolderProvider holders, ApplicationEventPublisher events, Clock clock) {
        this.roles = roles;
        this.permissions = permissions;
        this.mapper = mapper;
        this.holders = holders;
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
                Objects.requireNonNullElse(request.tone(), DEFAULT_TONE), false, granted, clock.instant()));
        if (request.assignment() != null) {
            role.changeAssignmentRules(request.assignment().maxDays(), request.assignment().reasonRequired(), clock.instant());
        }
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
        var name = Objects.requireNonNullElse(request.name(), role.getName());
        var description = Objects.requireNonNullElse(request.description(), role.getDescription());
        // A built-in role's name, description and permissions come from its RoleTemplate (P-019)
        if (role.isSystem() && (!added.isEmpty() || !removed.isEmpty() || !name.equals(role.getName())
                || !description.equals(role.getDescription()))) {
            throw SYSTEM_ROLE.exception();
        }
        if (!added.isEmpty() || !removed.isEmpty()) {
            // Nobody changes the permissions of a role they hold now
            if (actor.roleIds().contains(id)) {
                throw SELF_CHANGE.exception();
            }
            if (!GrantPolicy.holdsAll(actor.permissions(), union(added, removed))) {
                throw FORBIDDEN.exception();
            }
        }
        var tone = Objects.requireNonNullElse(request.tone(), role.getTone());
        var maxDays = request.assignment() == null ? role.getAssignmentMaxDays() : request.assignment().maxDays();
        var reasonRequired = request.assignment() == null ? role.isAssignmentReasonRequired() : request.assignment().reasonRequired();
        var changes = new ArrayList<FieldChange>();
        FieldChange.diff("name", role.getName(), name).ifPresent(changes::add);
        FieldChange.diff("description", role.getDescription(), description).ifPresent(changes::add);
        FieldChange.diff("tone", role.getTone(), tone).ifPresent(changes::add);
        // No limit is logged as "null"
        FieldChange.diff("maxDays", String.valueOf(role.getAssignmentMaxDays()), String.valueOf(maxDays))
                .ifPresent(changes::add);
        FieldChange.diff("reasonRequired", role.isAssignmentReasonRequired(), reasonRequired).ifPresent(changes::add);
        if (!added.isEmpty() || !removed.isEmpty()) {
            var order = mapper.order();
            changes.add(FieldChange.list("permissions", RoleMapper.ordered(added, order), RoleMapper.ordered(removed, order)));
        }
        role.update(name, description, tone, granted, clock.instant());
        role.changeAssignmentRules(maxDays, reasonRequired, clock.instant());
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
        // Held now or later: in use. Ended assignments of it go with it (on delete cascade)
        if (holders.heldNowOrLater(id)) {
            throw ROLE_IN_USE.exception();
        }
        var removed = RoleMapper.ordered(role.getPermissions(), mapper.order());
        events.publishEvent(new RoleDeleted(actor.userId(), id, role.getName(),
                List.of(FieldChange.list("permissions", List.of(), removed))));
        roles.delete(role);
        // The settings may still give it to new users: the foreign key fk_admin_settings_default_role refuses the
        // delete, and ApiExceptionHandler answers default_role. Flushing here makes that happen inside this transaction.
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
