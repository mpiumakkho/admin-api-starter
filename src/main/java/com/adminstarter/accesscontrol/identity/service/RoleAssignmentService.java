package com.adminstarter.accesscontrol.identity.service;

import static com.adminstarter.accesscontrol.common.constant.ErrorCode.ALREADY_ASSIGNED;
import static com.adminstarter.accesscontrol.common.constant.ErrorCode.ASSIGNMENT_TOO_LONG;
import static com.adminstarter.accesscontrol.common.constant.ErrorCode.FORBIDDEN;
import static com.adminstarter.accesscontrol.common.constant.ErrorCode.INVALID_REQUEST;
import static com.adminstarter.accesscontrol.common.constant.ErrorCode.LAST_ADMINISTRATOR;
import static com.adminstarter.accesscontrol.common.constant.ErrorCode.NOT_FOUND;
import static com.adminstarter.accesscontrol.common.constant.ErrorCode.REASON_REQUIRED;
import static com.adminstarter.accesscontrol.common.constant.ErrorCode.ROLE_CONFLICT;

import com.adminstarter.accesscontrol.authorization.CorePermissions;
import com.adminstarter.accesscontrol.authorization.dto.RoleResponse;
import com.adminstarter.accesscontrol.authorization.service.RoleConflictService;
import com.adminstarter.accesscontrol.authorization.service.RoleService;
import com.adminstarter.accesscontrol.branch.service.BranchService;
import com.adminstarter.accesscontrol.common.context.Actor;
import com.adminstarter.accesscontrol.common.dto.FieldChange;
import com.adminstarter.accesscontrol.identity.dto.CreateRoleAssignmentRequest;
import com.adminstarter.accesscontrol.identity.dto.RoleAssignmentBatchRequest;
import com.adminstarter.accesscontrol.identity.dto.RoleGrantRequest;
import com.adminstarter.accesscontrol.identity.dto.UpdateRoleAssignmentRequest;
import com.adminstarter.accesscontrol.identity.dto.UserResponse;
import com.adminstarter.accesscontrol.identity.entity.RoleAssignment;
import com.adminstarter.accesscontrol.identity.entity.User;
import com.adminstarter.accesscontrol.identity.event.RoleAssignmentAdded;
import com.adminstarter.accesscontrol.identity.event.RoleAssignmentRemoved;
import com.adminstarter.accesscontrol.identity.event.RoleAssignmentUpdated;
import com.adminstarter.accesscontrol.identity.policy.BranchScopePolicy;
import com.adminstarter.accesscontrol.identity.policy.SelfProtectionPolicy;
import com.adminstarter.accesscontrol.identity.repository.RoleAssignmentRepository;
import com.adminstarter.accesscontrol.settings.service.SettingsService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Giving, changing and taking away roles (P-019), with the rules in API.md, "Role assignments"; local-admin-api.ts is
 * the reference. Each assignment counts in its scope: users.edit (users.create for a new user's roles) and every
 * permission of the role, for the whole organization or in its branch (P-024). Every change evicts the cached
 * accounts, so the next request of the person sees it.
 */
@Service
@Transactional
public class RoleAssignmentService {

    private final RoleAssignmentRepository assignments;
    private final RoleAssignmentResolver resolver;
    private final UserAccessService access;
    private final RoleService roles;
    private final SettingsService settings;
    private final BranchService branches;
    private final RoleConflictService conflicts;
    private final UserMapper mapper;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public RoleAssignmentService(RoleAssignmentRepository assignments, RoleAssignmentResolver resolver,
            UserAccessService access, RoleService roles, SettingsService settings, BranchService branches,
            RoleConflictService conflicts, UserMapper mapper, ApplicationEventPublisher events, Clock clock) {
        this.assignments = assignments;
        this.resolver = resolver;
        this.access = access;
        this.roles = roles;
        this.settings = settings;
        this.branches = branches;
        this.conflicts = conflicts;
        this.mapper = mapper;
        this.events = events;
        this.clock = clock;
    }

    @CacheEvict(cacheNames = UserServiceImpl.ACCOUNTS_CACHE, allEntries = true)
    public UserResponse assign(Actor actor, CreateRoleAssignmentRequest request) {
        var user = access.findManageable(actor, request.userId(), CorePermissions.USERS_EDIT);
        SelfProtectionPolicy.forbidSelf(actor, List.of(user.getId()));
        actor.requireRecentSignIn();
        give(actor, user, request.grant(), resolver.of(user.getId()), CorePermissions.USERS_EDIT);
        return mapper.toResponse(user);
    }

    /** Extends, shortens or renews an assignment, or changes its reason */
    @CacheEvict(cacheNames = UserServiceImpl.ACCOUNTS_CACHE, allEntries = true)
    public UserResponse update(Actor actor, UUID id, UpdateRoleAssignmentRequest request) {
        var assignment = assignments.findById(id).orElseThrow(NOT_FOUND::exception);
        var user = changeable(actor, assignment);
        var role = requireChangeable(actor, assignment);
        actor.requireRecentSignIn();
        var now = clock.instant();
        var changes = new ArrayList<FieldChange>();
        changes.add(FieldChange.set("roleId", assignment.getRoleId().toString()));
        if (request.endsAt() != null) {
            var endsAt = request.endsAt().map(RoleAssignmentService::millis).orElse(null);
            if (!Objects.equals(endsAt, assignment.getEndsAt())) {
                // Still after the start and in the future; to end it now, remove it
                if (endsAt != null && (!endsAt.isAfter(now) || !endsAt.isAfter(assignment.getStartsAt()))) {
                    throw INVALID_REQUEST.exception();
                }
                requireWithinRules(role, latest(assignment.getStartsAt(), now), endsAt);
                var others = resolver.of(user.getId()).stream().filter(item -> !item.getId().equals(id)).toList();
                requireNoConflict(assignment.getRoleId(), assignment.getStartsAt(), endsAt, others, now);
                changes.add(FieldChange.of("endsAt", text(assignment.getEndsAt()), text(endsAt)));
                var hadAdministrators = resolver.administrators();
                assignment.moveEnd(endsAt);
                assignments.flush();
                requireAdministratorRemains(hadAdministrators);
            }
        }
        if (request.reason() != null && !request.reason().trim().equals(assignment.getReason())) {
            changes.add(FieldChange.of("reason", assignment.getReason(), request.reason().trim()));
            assignment.changeReason(request.reason().trim());
        }
        if (role.assignment().reasonRequired() && assignment.getReason().isBlank()) {
            throw REASON_REQUIRED.exception();
        }
        if (changes.size() > 1) {
            events.publishEvent(new RoleAssignmentUpdated(actor.userId(), user.getId(), user.getFullName(), List.copyOf(changes)));
        }
        return mapper.toResponse(user);
    }

    @CacheEvict(cacheNames = UserServiceImpl.ACCOUNTS_CACHE, allEntries = true)
    public UserResponse remove(Actor actor, UUID id, @Nullable String reason) {
        var assignment = assignments.findById(id).orElseThrow(NOT_FOUND::exception);
        var user = changeable(actor, assignment);
        requireChangeable(actor, assignment);
        actor.requireRecentSignIn();
        var why = settings.requireReason("removeRole", reason);
        var hadAdministrators = resolver.administrators();
        assignments.delete(assignment);
        assignments.flush();
        requireAdministratorRemains(hadAdministrators);
        events.publishEvent(new RoleAssignmentRemoved(actor.userId(), user.getId(), user.getFullName(),
                List.of(FieldChange.was("roleId", assignment.getRoleId().toString())), why));
        return mapper.toResponse(user);
    }

    /**
     * Gives several people a role, or takes one away from them: all or none. People with nothing to change are left out
     * of the answer and the log.
     */
    @CacheEvict(cacheNames = UserServiceImpl.ACCOUNTS_CACHE, allEntries = true)
    public List<UserResponse> batch(Actor actor, RoleAssignmentBatchRequest request) {
        // A role given carries its own reason in the grant; the request's goes with taking one away
        if ((request.add() == null) == (request.removeRoleId() == null) || (request.add() != null && request.reason() != null)) {
            throw INVALID_REQUEST.exception();
        }
        var ids = request.userIds().stream().distinct().toList();
        SelfProtectionPolicy.forbidSelf(actor, ids);
        var users = ids.stream().map(id -> access.findManageable(actor, id, CorePermissions.USERS_EDIT)).toList();
        actor.requireRecentSignIn();
        var now = clock.instant();
        var changed = new ArrayList<User>();
        if (request.add() != null) {
            var roleId = request.add().roleId();
            var branchId = request.add().branchId();
            for (var user : users) {
                var held = resolver.of(user.getId());
                // Held in the same scope: nothing to change
                if (held.stream().noneMatch(item -> item.getRoleId().equals(roleId)
                        && Objects.equals(item.getBranchId(), branchId) && item.heldAt(now))) {
                    give(actor, user, request.add(), held, CorePermissions.USERS_EDIT);
                    changed.add(user);
                }
            }
        } else {
            var roleId = request.removeRoleId();
            if (roles.find(roleId).isEmpty()) {
                throw NOT_FOUND.exception();
            }
            // Each assignment taken away in its scope, all checked before any goes
            var taken = new LinkedHashMap<User, List<RoleAssignment>>();
            for (var user : users) {
                var held = resolver.of(user.getId()).stream()
                        .filter(item -> item.getRoleId().equals(roleId) && item.heldAt(now))
                        .toList();
                held.forEach(item -> requireChangeable(actor, item));
                if (!held.isEmpty()) {
                    taken.put(user, held);
                }
            }
            var why = settings.requireReason("removeRole", request.reason());
            var hadAdministrators = resolver.administrators();
            taken.forEach((user, held) -> {
                assignments.deleteAll(held);
                events.publishEvent(new RoleAssignmentRemoved(actor.userId(), user.getId(), user.getFullName(),
                        List.of(FieldChange.was("roleId", roleId.toString())), why));
                changed.add(user);
            });
            assignments.flush();
            requireAdministratorRemains(hadAdministrators);
        }
        return changed.stream().map(mapper::toResponse).toList();
    }

    /** A new user's first roles; none gives the settings' default role, for good (see defaultGrant) */
    public void giveFirstRoles(Actor actor, User user, @Nullable List<RoleGrantRequest> grants) {
        var held = new ArrayList<RoleAssignment>();
        for (var grant : firstGrants(actor, grants, user.getBranchId())) {
            held.add(give(actor, user, grant, held, CorePermissions.USERS_CREATE));
        }
    }

    /** Checks a new user's roles as giving them would, without saving anything */
    public void requireCanGive(Actor actor, @Nullable List<RoleGrantRequest> grants, @Nullable UUID branchId) {
        var held = new ArrayList<RoleAssignment>();
        for (var grant : firstGrants(actor, grants, branchId)) {
            held.add(check(actor, UUID.randomUUID(), grant, held, CorePermissions.USERS_CREATE));
        }
    }

    /**
     * Runs a change that may take away the last person who can run the system (suspending or deleting someone), and
     * refuses it with last_administrator when it does
     */
    public <T> T keepingAdministrator(Supplier<T> change) {
        var hadAdministrators = resolver.administrators();
        var result = change.get();
        assignments.flush();
        requireAdministratorRemains(hadAdministrators);
        return result;
    }

    /**
     * The roles a new user is given, or the settings' default role for good: for the whole organization, or limited to
     * the new user's branch when the actor adds people only through branches (P-024)
     */
    private List<RoleGrantRequest> firstGrants(Actor actor, @Nullable List<RoleGrantRequest> grants, @Nullable UUID branchId) {
        if (grants != null && !grants.isEmpty()) {
            return grants;
        }
        var scope = actor.has(CorePermissions.USERS_CREATE) ? null : branchId;
        return List.of(new RoleGrantRequest(settings.current().defaultRoleId(), scope, null, null, null));
    }

    /** An assignment of a role to someone, checked as API.md says, and saved with its entry in the log */
    private RoleAssignment give(Actor actor, User user, RoleGrantRequest grant, List<RoleAssignment> held, String permission) {
        var assignment = assignments.save(check(actor, user.getId(), grant, held, permission));
        var changes = new ArrayList<FieldChange>();
        changes.add(FieldChange.set("roleId", assignment.getRoleId().toString()));
        if (assignment.getBranchId() != null) {
            changes.add(FieldChange.set("branchId", assignment.getBranchId().toString()));
        }
        if (assignment.getStartsAt().isAfter(assignment.getAssignedAt())) {
            changes.add(FieldChange.set("startsAt", assignment.getStartsAt().toString()));
        }
        if (assignment.getEndsAt() != null) {
            changes.add(FieldChange.set("endsAt", assignment.getEndsAt().toString()));
        }
        // Its reason is the entry's (P-021)
        events.publishEvent(new RoleAssignmentAdded(actor.userId(), user.getId(), user.getFullName(), List.copyOf(changes),
                assignment.getReason()));
        return assignment;
    }

    /**
     * The actor may give the role in its scope (the permission and every permission of the role there), the branch is
     * open, the dates make sense, the role's own rules hold, and it does not overlap one of the same role in the same
     * scope the person already has
     */
    private RoleAssignment check(Actor actor, UUID userId, RoleGrantRequest grant, List<RoleAssignment> held, String permission) {
        var branchId = grant.branchId();
        if (!BranchScopePolicy.allows(actor, permission, branchId)) {
            throw FORBIDDEN.exception();
        }
        var role = roles.requireAssignable(actor, grant.roleId(), branchId);
        if (branchId != null) {
            branches.requireOpen(branchId);
        }
        var now = clock.instant();
        var start = grant.startsAt() == null ? now : latest(millis(grant.startsAt()), now);
        var end = grant.endsAt() == null ? null : millis(grant.endsAt());
        if (end != null && !end.isAfter(start)) {
            throw INVALID_REQUEST.exception();
        }
        requireWithinRules(role, start, end);
        var reason = Objects.requireNonNullElse(grant.reason(), "").trim();
        if (role.assignment().reasonRequired() && reason.isEmpty()) {
            throw REASON_REQUIRED.exception();
        }
        if (held.stream().anyMatch(item -> item.getRoleId().equals(grant.roleId())
                && Objects.equals(item.getBranchId(), branchId) && item.overlaps(start, end))) {
            throw ALREADY_ASSIGNED.exception();
        }
        requireNoConflict(grant.roleId(), start, end, held, now);
        return new RoleAssignment(userId, grant.roleId(), branchId, start, end, reason, actor.userId(), now);
    }

    /** The person an assignment belongs to: someone the actor may change, and not the actor */
    private User changeable(Actor actor, RoleAssignment assignment) {
        var user = access.findManageable(actor, assignment.getUserId(), CorePermissions.USERS_EDIT);
        SelfProtectionPolicy.forbidSelf(actor, List.of(user.getId()));
        return user;
    }

    /** The actor has users.edit and every permission of the role in the assignment's scope (P-024) */
    private RoleResponse requireChangeable(Actor actor, RoleAssignment assignment) {
        if (!BranchScopePolicy.allows(actor, CorePermissions.USERS_EDIT, assignment.getBranchId())) {
            throw FORBIDDEN.exception();
        }
        return roles.requireAssignable(actor, assignment.getRoleId(), assignment.getBranchId());
    }

    /**
     * No rule says the role cannot be held with another the person holds, now or later, at a time the period overlaps;
     * in any scope (P-022)
     */
    private void requireNoConflict(UUID roleId, Instant start, @Nullable Instant end, List<RoleAssignment> held, Instant now) {
        for (var rule : conflicts.rulesWith(roleId)) {
            if (held.stream().anyMatch(item -> !item.getRoleId().equals(roleId) && rule.roleIds().contains(item.getRoleId())
                    && item.heldAt(now) && item.overlaps(start, end))) {
                throw ROLE_CONFLICT.exception();
            }
        }
    }

    private void requireAdministratorRemains(long before) {
        if (before > 0 && resolver.administrators() == 0) {
            throw LAST_ADMINISTRATOR.exception();
        }
    }

    /** A role's rules: with maxDays, it must end at most that many days after it starts */
    private static void requireWithinRules(RoleResponse role, Instant start, @Nullable Instant end) {
        var maxDays = role.assignment().maxDays();
        if (maxDays != null && (end == null || Duration.between(start, end).compareTo(Duration.ofDays(maxDays)) > 0)) {
            throw ASSIGNMENT_TOO_LONG.exception();
        }
    }

    private static Instant millis(Instant instant) {
        return instant.truncatedTo(ChronoUnit.MILLIS);
    }

    private static Instant latest(Instant a, Instant b) {
        return a.isAfter(b) ? a : b;
    }

    private static String text(@Nullable Instant instant) {
        return instant == null ? "" : instant.toString();
    }
}
