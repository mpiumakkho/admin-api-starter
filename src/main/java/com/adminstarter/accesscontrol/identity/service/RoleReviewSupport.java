package com.adminstarter.accesscontrol.identity.service;

import com.adminstarter.accesscontrol.authorization.CorePermissions;
import com.adminstarter.accesscontrol.authorization.service.RoleService;
import com.adminstarter.accesscontrol.common.context.Actor;
import com.adminstarter.accesscontrol.common.dto.FieldChange;
import com.adminstarter.accesscontrol.identity.dto.RoleHolderSummary;
import com.adminstarter.accesscontrol.identity.entity.RoleAssignment;
import com.adminstarter.accesscontrol.identity.entity.User;
import com.adminstarter.accesscontrol.identity.entity.UserStatus;
import com.adminstarter.accesscontrol.identity.event.RoleAssignmentRemoved;
import com.adminstarter.accesscontrol.identity.policy.BranchScopePolicy;
import com.adminstarter.accesscontrol.identity.repository.RoleAssignmentRepository;
import com.adminstarter.accesscontrol.identity.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What access reviews (P-028) ask of identity: the role assignments a new review covers, who may decide about each,
 * and taking one away once a review has decided so. It checks a whole review at once, reading the people and their
 * assignments a single time, since a review may cover thousands of assignments.
 */
@Service
@Transactional(readOnly = true)
public class RoleReviewSupport {

    /** Not a session: the actor stands for someone who is not making a request, to find out what they may decide */
    private static final UUID NO_SESSION = new UUID(0, 0);

    private final UserRepository users;
    private final RoleAssignmentRepository assignments;
    private final RoleAssignmentResolver resolver;
    private final RoleService roles;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public RoleReviewSupport(UserRepository users, RoleAssignmentRepository assignments, RoleAssignmentResolver resolver,
            RoleService roles, ApplicationEventPublisher events, Clock clock) {
        this.users = users;
        this.assignments = assignments;
        this.resolver = resolver;
        this.roles = roles;
        this.events = events;
        this.clock = clock;
    }

    /** Every assignment held now or later, of one role or of every role (null), by the people of one branch or everyone */
    public List<RoleHolderSummary> holders(@Nullable UUID roleId, @Nullable UUID branchId) {
        var now = clock.instant();
        var people = (branchId == null ? users.findAll() : users.findByBranchId(branchId)).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        var given = branchId == null ? assignments.findAllByOrderByStartsAtAscIdAsc() : assignments.findByUserIdIn(people.keySet());
        return given.stream()
                .filter(item -> item.heldAt(now) && (roleId == null || roleId.equals(item.getRoleId())))
                .map(item -> {
                    var person = people.get(item.getUserId());
                    return new RoleHolderSummary(item.getId(), item.getUserId(), person.getFullName(), person.getEmail(),
                            item.getRoleId(), item.getBranchId(), item.getStartsAt(), item.getEndsAt());
                })
                .toList();
    }

    /**
     * Of these assignments, by id, the ones the actor may decide about: they look after the holder, who still exists,
     * as users.edit does (rules 1, 3 and 4), and have every permission of the role in the assignment's scope. Not the
     * assignment's own scope's users.edit, as taking it away would need: a decision changes nothing until the review
     * closes. Never their own.
     */
    public Set<UUID> decidableBy(Actor actor, Collection<RoleHolderSummary> held) {
        return new Holders(held).decidableBy(actor);
    }

    /** For each active person who may decide some of these, how many: whom to tell that a review waits for them */
    public Map<UUID, Integer> deciders(Collection<RoleHolderSummary> held) {
        var holders = new Holders(held);
        var now = clock.instant();
        var given = resolver.byUser();
        var active = users.findAllById(given.keySet()).stream()
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .map(User::getId)
                .collect(Collectors.toSet());
        var deciders = new LinkedHashMap<UUID, Integer>();
        given.forEach((userId, theirs) -> {
            var actor = actorOf(userId, theirs, now);
            if (active.contains(userId) && looksAfterSomeone(actor)) {
                var count = holders.decidableBy(actor).size();
                if (count > 0) {
                    deciders.put(userId, count);
                }
            }
        });
        return deciders;
    }

    /** What taking away an assignment a review decided to remove did */
    public enum Removal {
        REMOVED,
        /** It had ended, or been taken away, already */
        GONE,
        /** Nobody else would be left to run the system (rule 6): it stays */
        LAST_ADMINISTRATOR
    }

    /**
     * Takes away an assignment that a review decided to remove, as the server: the log has no actor, the review's name as
     * accessReview, and the decision's note as the reason
     */
    @Transactional
    @CacheEvict(cacheNames = UserServiceImpl.ACCOUNTS_CACHE, allEntries = true)
    public Removal removeReviewed(UUID assignmentId, String reviewName, String note) {
        var now = clock.instant();
        var found = assignments.findById(assignmentId).filter(item -> item.heldAt(now));
        var holder = found.flatMap(item -> users.findById(item.getUserId()));
        if (found.isEmpty() || holder.isEmpty()) {
            return Removal.GONE;
        }
        var assignment = found.get();
        var user = holder.get();
        if (lastAdministrator(assignment, user, now)) {
            return Removal.LAST_ADMINISTRATOR;
        }
        assignments.delete(assignment);
        assignments.flush();
        events.publishEvent(new RoleAssignmentRemoved(null, user.getId(), user.getFullName(), List.of(
                FieldChange.was("roleId", assignment.getRoleId().toString()), FieldChange.set("accessReview", reviewName)), note));
        return Removal.REMOVED;
    }

    /**
     * Whether this assignment is what keeps its holder an administrator (a role with every permission, now, for good, for
     * the whole organization), and they are the last active one
     */
    private boolean lastAdministrator(RoleAssignment assignment, User user, Instant now) {
        var full = roles.fullAccessRoleIds();
        if (user.getStatus() != UserStatus.ACTIVE || !makesAdministrator(assignment, full, now)) {
            return false;
        }
        var another = assignments.findByUserIdOrderByStartsAtAscIdAsc(user.getId()).stream()
                .anyMatch(item -> !item.getId().equals(assignment.getId()) && makesAdministrator(item, full, now));
        return !another && resolver.administrators() <= 1;
    }

    private static boolean makesAdministrator(RoleAssignment item, Set<UUID> full, Instant now) {
        return full.contains(item.getRoleId()) && item.getBranchId() == null && item.getEndsAt() == null && item.activeAt(now);
    }

    /**
     * Someone as they would act, from the assignments they hold at this moment, the way PermissionAuthenticationConverter
     * makes the actor of a request: for checking what they may decide
     */
    private Actor actorOf(UUID userId, List<RoleAssignment> theirs, Instant now) {
        var held = theirs.stream().filter(item -> item.activeAt(now)).toList();
        var organization = new HashSet<String>();
        var branches = new HashMap<UUID, Set<String>>();
        for (var item : held) {
            var granted = item.getBranchId() == null ? organization : branches.computeIfAbsent(item.getBranchId(), id -> new HashSet<>());
            granted.addAll(roles.permissionsOf(item.getRoleId()));
        }
        var roleIds = held.stream().map(RoleAssignment::getRoleId).collect(Collectors.toSet());
        return new Actor(userId, NO_SESSION, roleIds, organization, branches, false, true);
    }

    /** Whether they have users.edit anywhere: for the whole organization, or over the people of a branch */
    private static boolean looksAfterSomeone(Actor actor) {
        return actor.has(CorePermissions.USERS_EDIT)
                || !BranchScopePolicy.branchesWith(actor, CorePermissions.USERS_EDIT).isEmpty();
    }

    /** The assignments of a review, with their holders as they are now and what each holds now or later */
    private final class Holders {

        private final Collection<RoleHolderSummary> held;
        private final Map<UUID, User> people;
        private final Map<UUID, List<RoleAssignment>> heldBy;

        Holders(Collection<RoleHolderSummary> held) {
            this.held = held;
            var ids = held.stream().map(RoleHolderSummary::userId).collect(Collectors.toSet());
            this.people = users.findAllById(ids).stream().collect(Collectors.toMap(User::getId, Function.identity()));
            var now = clock.instant();
            this.heldBy = assignments.findByUserIdIn(ids).stream()
                    .filter(item -> item.heldAt(now))
                    .collect(Collectors.groupingBy(RoleAssignment::getUserId));
        }

        Set<UUID> decidableBy(Actor actor) {
            // Whether they look after each person, worked out once per person
            var looksAfter = new HashMap<UUID, Boolean>();
            return held.stream()
                    .filter(item -> !actor.is(item.userId()) && people.containsKey(item.userId())
                            && looksAfter.computeIfAbsent(item.userId(), id -> looksAfter(actor, people.get(id)))
                            && roles.canManageHolderOf(actor, item.roleId(), item.branchId()))
                    .map(RoleHolderSummary::assignmentId)
                    .collect(Collectors.toSet());
        }

        /** users.edit over the person's branch, and every permission of each role they hold now or later in its scope */
        private boolean looksAfter(Actor actor, User person) {
            return BranchScopePolicy.allows(actor, CorePermissions.USERS_EDIT, person.getBranchId())
                    && heldBy.getOrDefault(person.getId(), List.of()).stream()
                            .allMatch(item -> roles.canManageHolderOf(actor, item.getRoleId(), item.getBranchId()));
        }
    }
}
