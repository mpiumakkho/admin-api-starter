package com.adminstarter.accesscontrol.identity.service;

import static com.adminstarter.accesscontrol.common.constant.ErrorCode.ALREADY_ASSIGNED;
import static com.adminstarter.accesscontrol.common.constant.ErrorCode.ASSIGNMENT_TOO_LONG;
import static com.adminstarter.accesscontrol.common.constant.ErrorCode.INVALID_REQUEST;
import static com.adminstarter.accesscontrol.common.constant.ErrorCode.LAST_ADMINISTRATOR;
import static com.adminstarter.accesscontrol.common.constant.ErrorCode.NOT_FOUND;
import static com.adminstarter.accesscontrol.common.constant.ErrorCode.REASON_REQUIRED;

import com.adminstarter.accesscontrol.authorization.dto.RoleResponse;
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
import com.adminstarter.accesscontrol.identity.policy.SelfProtectionPolicy;
import com.adminstarter.accesscontrol.identity.repository.RoleAssignmentRepository;
import com.adminstarter.accesscontrol.settings.service.SettingsService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
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
 * the reference. Every change evicts the cached accounts, so the next request of the person sees it.
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
    private final UserMapper mapper;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public RoleAssignmentService(RoleAssignmentRepository assignments, RoleAssignmentResolver resolver,
            UserAccessService access, RoleService roles, SettingsService settings, BranchService branches,
            UserMapper mapper, ApplicationEventPublisher events, Clock clock) {
        this.assignments = assignments;
        this.resolver = resolver;
        this.access = access;
        this.roles = roles;
        this.settings = settings;
        this.branches = branches;
        this.mapper = mapper;
        this.events = events;
        this.clock = clock;
    }

    @CacheEvict(cacheNames = UserServiceImpl.ACCOUNTS_CACHE, allEntries = true)
    public UserResponse assign(Actor actor, CreateRoleAssignmentRequest request) {
        var user = access.findManageable(actor, request.userId());
        SelfProtectionPolicy.forbidSelf(actor, List.of(user.getId()));
        give(actor, user, request.grant(), resolver.of(user.getId()));
        return mapper.toResponse(user);
    }

    /** Extends, shortens or renews an assignment, or changes its reason */
    @CacheEvict(cacheNames = UserServiceImpl.ACCOUNTS_CACHE, allEntries = true)
    public UserResponse update(Actor actor, UUID id, UpdateRoleAssignmentRequest request) {
        var assignment = assignments.findById(id).orElseThrow(NOT_FOUND::exception);
        var user = changeable(actor, assignment);
        var role = roles.requireAssignable(actor, assignment.getRoleId());
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
    public UserResponse remove(Actor actor, UUID id) {
        var assignment = assignments.findById(id).orElseThrow(NOT_FOUND::exception);
        var user = changeable(actor, assignment);
        roles.requireAssignable(actor, assignment.getRoleId());
        var hadAdministrators = resolver.administrators();
        assignments.delete(assignment);
        assignments.flush();
        requireAdministratorRemains(hadAdministrators);
        events.publishEvent(new RoleAssignmentRemoved(actor.userId(), user.getId(), user.getFullName(),
                List.of(FieldChange.was("roleId", assignment.getRoleId().toString()))));
        return mapper.toResponse(user);
    }

    /**
     * Gives several people a role, or takes one away from them: all or none. People with nothing to change are left out
     * of the answer and the log.
     */
    @CacheEvict(cacheNames = UserServiceImpl.ACCOUNTS_CACHE, allEntries = true)
    public List<UserResponse> batch(Actor actor, RoleAssignmentBatchRequest request) {
        if ((request.add() == null) == (request.removeRoleId() == null)) {
            throw INVALID_REQUEST.exception();
        }
        var ids = request.userIds().stream().distinct().toList();
        SelfProtectionPolicy.forbidSelf(actor, ids);
        var users = ids.stream().map(id -> access.findManageable(actor, id)).toList();
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
                    give(actor, user, request.add(), held);
                    changed.add(user);
                }
            }
        } else {
            var roleId = request.removeRoleId();
            roles.requireAssignable(actor, roleId);
            var hadAdministrators = resolver.administrators();
            for (var user : users) {
                var taken = resolver.of(user.getId()).stream()
                        .filter(item -> item.getRoleId().equals(roleId) && item.heldAt(now))
                        .toList();
                if (!taken.isEmpty()) {
                    assignments.deleteAll(taken);
                    events.publishEvent(new RoleAssignmentRemoved(actor.userId(), user.getId(), user.getFullName(),
                            List.of(FieldChange.was("roleId", roleId.toString()))));
                    changed.add(user);
                }
            }
            assignments.flush();
            requireAdministratorRemains(hadAdministrators);
        }
        return changed.stream().map(mapper::toResponse).toList();
    }

    /** A new user's first roles; none gives the settings' default role, for good */
    public void giveFirstRoles(Actor actor, User user, @Nullable List<RoleGrantRequest> grants) {
        var given = grants == null || grants.isEmpty()
                ? List.of(new RoleGrantRequest(settings.current().defaultRoleId(), null, null, null, null))
                : grants;
        var held = new ArrayList<RoleAssignment>();
        for (var grant : given) {
            held.add(give(actor, user, grant, held));
        }
    }

    /** Checks a new user's roles as giving them would, without saving anything */
    public void requireCanGive(Actor actor, @Nullable List<RoleGrantRequest> grants) {
        var given = grants == null || grants.isEmpty()
                ? List.of(new RoleGrantRequest(settings.current().defaultRoleId(), null, null, null, null))
                : grants;
        var held = new ArrayList<RoleAssignment>();
        for (var grant : given) {
            held.add(check(actor, UUID.randomUUID(), grant, held));
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

    /** An assignment of a role to someone, checked as API.md says, and saved with its entry in the log */
    private RoleAssignment give(Actor actor, User user, RoleGrantRequest grant, List<RoleAssignment> held) {
        var assignment = assignments.save(check(actor, user.getId(), grant, held));
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
        if (!assignment.getReason().isEmpty()) {
            changes.add(FieldChange.set("reason", assignment.getReason()));
        }
        events.publishEvent(new RoleAssignmentAdded(actor.userId(), user.getId(), user.getFullName(), List.copyOf(changes)));
        return assignment;
    }

    /**
     * The actor may give the role, the branch is open, the dates make sense, the role's own rules hold, and it does not
     * overlap one of the same role in the same scope the person already has
     */
    private RoleAssignment check(Actor actor, UUID userId, RoleGrantRequest grant, List<RoleAssignment> held) {
        var role = roles.requireAssignable(actor, grant.roleId());
        var branchId = grant.branchId();
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
        return new RoleAssignment(userId, grant.roleId(), branchId, start, end, reason, actor.userId(), now);
    }

    /** The person an assignment belongs to: someone the actor may change, and not the actor */
    private User changeable(Actor actor, RoleAssignment assignment) {
        var user = access.findManageable(actor, assignment.getUserId());
        SelfProtectionPolicy.forbidSelf(actor, List.of(user.getId()));
        return user;
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
