package com.adminstarter.accesscontrol.review.service;

import static com.adminstarter.accesscontrol.common.constant.ErrorCode.FORBIDDEN;
import static com.adminstarter.accesscontrol.common.constant.ErrorCode.INVALID_REQUEST;
import static com.adminstarter.accesscontrol.common.constant.ErrorCode.NOT_FOUND;
import static com.adminstarter.accesscontrol.common.constant.ErrorCode.REVIEW_CLOSED;
import static com.adminstarter.accesscontrol.common.constant.ErrorCode.SELF_CHANGE;

import com.adminstarter.accesscontrol.authorization.CorePermissions;
import com.adminstarter.accesscontrol.authorization.dto.RoleResponse;
import com.adminstarter.accesscontrol.authorization.service.RoleService;
import com.adminstarter.accesscontrol.branch.service.BranchService;
import com.adminstarter.accesscontrol.common.context.Actor;
import com.adminstarter.accesscontrol.common.dto.FieldChange;
import com.adminstarter.accesscontrol.identity.dto.AccountSummary;
import com.adminstarter.accesscontrol.identity.dto.RoleHolderSummary;
import com.adminstarter.accesscontrol.identity.service.RoleReviewSupport;
import com.adminstarter.accesscontrol.identity.service.UserService;
import com.adminstarter.accesscontrol.settings.service.SettingsService;
import com.adminstarter.accesscontrol.review.dto.AccessReviewDecisionRequest;
import com.adminstarter.accesscontrol.review.dto.AccessReviewItemResponse;
import com.adminstarter.accesscontrol.review.dto.AccessReviewResponse;
import com.adminstarter.accesscontrol.review.dto.StartAccessReviewRequest;
import com.adminstarter.accesscontrol.review.entity.AccessReview;
import com.adminstarter.accesscontrol.review.entity.AccessReviewDecision;
import com.adminstarter.accesscontrol.review.entity.AccessReviewItem;
import com.adminstarter.accesscontrol.review.entity.AccessReviewOutcome;
import com.adminstarter.accesscontrol.review.event.AccessReviewClosed;
import com.adminstarter.accesscontrol.review.event.AccessReviewDecided;
import com.adminstarter.accesscontrol.review.event.AccessReviewDeleted;
import com.adminstarter.accesscontrol.review.event.AccessReviewStarted;
import com.adminstarter.accesscontrol.review.repository.AccessReviewItemRepository;
import com.adminstarter.accesscontrol.review.repository.AccessReviewRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Access reviews (P-028), with the rules in API.md, "Access reviews"; local-admin-api.ts is the reference. People with
 * reviews.manage start, close and delete them and see all of them; the people who look after each person, and those a
 * review names, decide. Closing takes away the roles decided Remove, through identity's RoleReviewSupport.
 */
@Service
@Transactional
public class AccessReviewService {

    /** The longest a review may stay open */
    private static final Duration LONGEST = Duration.ofDays(365);

    private final AccessReviewRepository reviews;
    private final AccessReviewItemRepository items;
    private final RoleReviewSupport support;
    private final UserService users;
    private final RoleService roles;
    private final BranchService branches;
    private final SettingsService settings;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public AccessReviewService(AccessReviewRepository reviews, AccessReviewItemRepository items, RoleReviewSupport support,
            UserService users, RoleService roles, BranchService branches,
            SettingsService settings, ApplicationEventPublisher events,
            Clock clock) {
        this.reviews = reviews;
        this.items = items;
        this.support = support;
        this.users = users;
        this.roles = roles;
        this.branches = branches;
        this.settings = settings;
        this.events = events;
        this.clock = clock;
    }

    /**
     * Newest first: every review with reviews.manage; otherwise the ones that name the actor, the open ones with a role
     * they may decide and the closed ones where they decided some. Only open reviews' roles are read, for waitingCount.
     */
    @Transactional(readOnly = true)
    public List<AccessReviewResponse> list(Actor actor) {
        var counts = items.countByReview().stream()
                .collect(Collectors.toMap(AccessReviewItemRepository.Counts::getReviewId, Function.identity()));
        var shown = new ArrayList<AccessReviewResponse>();
        for (var review : reviews.findAllByOrderByCreatedAtDescIdDesc()) {
            var takesPart = sees(actor, review);
            long waiting = 0;
            if (review.isOpen()) {
                var all = itemsOf(review);
                var decidable = decidable(actor, review, all);
                takesPart = takesPart || !decidable.isEmpty();
                waiting = all.stream().filter(item -> item.getDecision() == null && decidable.contains(item.getId())).count();
            } else if (!takesPart) {
                takesPart = items.existsByReviewIdAndDecidedBy(review.getId(), actor.userId());
            }
            if (takesPart) {
                var count = counts.get(review.getId());
                shown.add(toResponse(review, count == null ? 0 : count.getTotal(), count == null ? 0 : count.getDecided(), waiting));
            }
        }
        return shown;
    }

    public AccessReviewResponse start(Actor actor, StartAccessReviewRequest request) {
        var name = request.name().trim();
        var now = clock.instant();
        var dueAt = request.dueAt().truncatedTo(ChronoUnit.MILLIS);
        var reviewerIds = Objects.requireNonNullElse(request.reviewerIds(), List.<UUID>of());
        if (name.isEmpty() || !dueAt.isAfter(now) || dueAt.isAfter(now.plus(LONGEST))
                || new HashSet<>(reviewerIds).size() != reviewerIds.size()) {
            throw INVALID_REQUEST.exception();
        }
        var roleId = request.roleId();
        var branchId = request.branchId();
        if ((roleId != null && roles.find(roleId).isEmpty()) || (branchId != null && branches.nameOf(branchId).isEmpty())
                || reviewerIds.stream().anyMatch(id -> users.findAccount(id).isEmpty())) {
            throw NOT_FOUND.exception();
        }
        // Every role held now or later by the people in scope, at this moment, by person and then role
        var roleNames = new HashMap<UUID, String>();
        Function<UUID, String> roleName = id -> roleNames.computeIfAbsent(id, key -> roles.find(key).map(RoleResponse::name).orElse(""));
        var holders = support.holders(roleId, branchId).stream()
                .sorted(Comparator.comparing(RoleHolderSummary::fullName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(holder -> roleName.apply(holder.roleId()), String.CASE_INSENSITIVE_ORDER))
                .toList();
        var review = reviews.save(new AccessReview(name, roleId, branchId, Set.copyOf(reviewerIds), dueAt, actor.userId(), now));
        var saved = items.saveAll(holders.stream().map(holder -> new AccessReviewItem(review.getId(), holder.assignmentId(),
                holder.userId(), holder.fullName(), holder.email(), holder.roleId(), roleName.apply(holder.roleId()),
                holder.branchId(), holder.startsAt(), holder.endsAt())).toList());

        var changes = new ArrayList<FieldChange>();
        if (roleId != null) {
            changes.add(FieldChange.set("roleId", roleId.toString()));
        }
        if (branchId != null) {
            changes.add(FieldChange.set("branchId", branchId.toString()));
        }
        changes.add(FieldChange.set("dueAt", dueAt.toString()));
        if (!reviewerIds.isEmpty()) {
            changes.add(FieldChange.list("reviewers", reviewerIds.stream().map(UUID::toString).toList(), List.of()));
        }
        changes.add(FieldChange.set("assignments", String.valueOf(saved.size())));
        events.publishEvent(new AccessReviewStarted(actor.userId(), review.getId(), name, List.copyOf(changes), dueAt,
                reviewersOf(review, holders)));
        return toResponse(review, saved.size(), 0, decidable(actor, review, saved).size());
    }

    /**
     * The roles under review the actor sees: all of them with reviews.manage or when it names them; otherwise the ones
     * they may decide, or, once it is closed, the ones they decided. not_found for a review they take no part in.
     */
    @Transactional(readOnly = true)
    public List<AccessReviewItemResponse> items(Actor actor, UUID reviewId) {
        var review = reviews.findById(reviewId).orElseThrow(NOT_FOUND::exception);
        var all = itemsOf(review);
        var decidable = review.isOpen() ? decidable(actor, review, all) : Set.<UUID>of();
        var everything = sees(actor, review);
        var shown = everything ? all : all.stream()
                .filter(item -> review.isOpen() ? decidable.contains(item.getId()) : actor.userId().equals(item.getDecidedBy()))
                .toList();
        if (!everything && shown.isEmpty()) {
            throw NOT_FOUND.exception();
        }
        return shown.stream().map(item -> toResponse(item, decidable.contains(item.getId()))).toList();
    }

    /** Keeps or removes each of these roles when the review closes: all or none; the latest decision counts */
    public List<AccessReviewItemResponse> decide(Actor actor, UUID reviewId, AccessReviewDecisionRequest request) {
        var review = reviews.findForUpdate(reviewId).orElseThrow(NOT_FOUND::exception);
        var all = itemsOf(review);
        var decidable = review.isOpen() ? decidable(actor, review, all) : Set.<UUID>of();
        var takesPart = sees(actor, review) || !decidable.isEmpty()
                || all.stream().anyMatch(item -> actor.userId().equals(item.getDecidedBy()));
        if (!takesPart) {
            throw NOT_FOUND.exception();
        }
        if (!review.isOpen()) {
            throw REVIEW_CLOSED.exception();
        }
        var byId = all.stream().collect(Collectors.toMap(AccessReviewItem::getId, Function.identity()));
        var chosen = request.itemIds().stream().distinct().map(id -> {
            var item = byId.get(id);
            if (item == null) {
                throw NOT_FOUND.exception();
            }
            return item;
        }).toList();
        if (chosen.stream().anyMatch(item -> actor.is(item.getUserId()))) {
            throw SELF_CHANGE.exception();
        }
        if (chosen.stream().anyMatch(item -> !decidable.contains(item.getId()))) {
            throw FORBIDDEN.exception();
        }
        var decision = AccessReviewDecision.of(request.decision());
        // Taking a role away asks for a reason where the settings say so, as removing it does (P-021)
        var note = decision == AccessReviewDecision.REMOVE
                ? settings.requireReason("removeRole", request.note())
                : Objects.requireNonNullElse(request.note(), "").trim();
        var decidedBy = users.findAccount(actor.userId()).map(AccountSummary::fullName).orElse("");
        var now = clock.instant();
        chosen.forEach(item -> item.decide(decision, note, actor.userId(), decidedBy, now));
        events.publishEvent(new AccessReviewDecided(actor.userId(), review.getId(), review.getName(), List.of(
                FieldChange.set("decision", decision.value()), FieldChange.set("assignments", String.valueOf(chosen.size()))), note));
        return chosen.stream().map(item -> toResponse(item, true)).toList();
    }

    /** Closes it now, taking away the roles decided Remove: a risky change (P-023) */
    public AccessReviewResponse close(Actor actor, UUID reviewId) {
        var review = reviews.findForUpdate(reviewId).orElseThrow(NOT_FOUND::exception);
        if (!review.isOpen()) {
            throw REVIEW_CLOSED.exception();
        }
        actor.requireRecentSignIn();
        var all = closeReview(review, actor.userId());
        return toResponse(review, all.size(), all.stream().filter(item -> item.getDecision() != null).count(), 0);
    }

    /** Closes the open reviews whose due date has passed, as the server does by itself: AccessReviewJob, every hour */
    public int closeDue() {
        var due = reviews.findDueForUpdate(clock.instant());
        due.forEach(review -> closeReview(review, null));
        return due.size();
    }

    /** An open one only: a closed review is the record */
    public void delete(Actor actor, UUID reviewId) {
        var review = reviews.findForUpdate(reviewId).orElseThrow(NOT_FOUND::exception);
        if (!review.isOpen()) {
            throw REVIEW_CLOSED.exception();
        }
        events.publishEvent(new AccessReviewDeleted(actor.userId(), review.getName()));
        // Its roles go with it (on delete cascade)
        reviews.delete(review);
        reviews.flush();
    }

    /**
     * Takes away the roles decided Remove, unless gone or the last administrator's, notes what became of each, and
     * closes it: by someone, or by the server (null)
     */
    private List<AccessReviewItem> closeReview(AccessReview review, @Nullable UUID closedBy) {
        var all = itemsOf(review);
        for (var item : all) {
            item.settle(switch (item.getDecision()) {
                case null -> AccessReviewOutcome.NOT_REVIEWED;
                case KEEP -> AccessReviewOutcome.KEPT;
                case REMOVE -> switch (support.removeReviewed(item.getAssignmentId(), review.getName(), item.getNote())) {
                    case REMOVED -> AccessReviewOutcome.REMOVED;
                    case GONE -> AccessReviewOutcome.GONE;
                    case LAST_ADMINISTRATOR -> AccessReviewOutcome.LAST_ADMINISTRATOR;
                };
            });
        }
        review.close(closedBy, clock.instant());
        Function<AccessReviewOutcome, String> count = outcome -> String.valueOf(all.stream().filter(item -> item.getOutcome() == outcome).count());
        events.publishEvent(new AccessReviewClosed(closedBy, review.getId(), review.getName(), List.of(
                FieldChange.set("kept", count.apply(AccessReviewOutcome.KEPT)),
                FieldChange.set("removed", count.apply(AccessReviewOutcome.REMOVED)),
                FieldChange.set("notReviewed", count.apply(AccessReviewOutcome.NOT_REVIEWED)))));
        return all;
    }

    /** Whether the actor sees all of a review: with reviews.manage, or when it names them */
    private static boolean sees(Actor actor, AccessReview review) {
        return actor.has(CorePermissions.REVIEWS_MANAGE) || review.names(actor.userId());
    }

    private List<AccessReviewItem> itemsOf(AccessReview review) {
        return items.findByReviewIdOrderByUserNameAscRoleNameAscIdAsc(review.getId());
    }

    /**
     * The roles of an open review the actor may decide, by item id: all but their own when it names them; otherwise the
     * ones of people they look after (P-028)
     */
    private Set<UUID> decidable(Actor actor, AccessReview review, List<AccessReviewItem> all) {
        if (review.names(actor.userId())) {
            return all.stream().filter(item -> !actor.is(item.getUserId())).map(AccessReviewItem::getId).collect(Collectors.toSet());
        }
        var assignments = support.decidableBy(actor, all.stream().map(AccessReviewService::holder).toList());
        return all.stream().filter(item -> assignments.contains(item.getAssignmentId())).map(AccessReviewItem::getId)
                .collect(Collectors.toSet());
    }

    /** Who may decide some of a new review, and how many, for the emails: the people's administrators, and those it names */
    private List<AccessReviewStarted.Reviewer> reviewersOf(AccessReview review, List<RoleHolderSummary> holders) {
        var counts = new LinkedHashMap<UUID, Integer>(support.deciders(holders));
        for (var id : review.getReviewerIds()) {
            counts.put(id, (int) holders.stream().filter(holder -> !holder.userId().equals(id)).count());
        }
        var reviewers = new ArrayList<AccessReviewStarted.Reviewer>();
        counts.forEach((userId, count) -> users.findAccount(userId)
                .filter(account -> account.active() && count > 0)
                .ifPresent(account -> reviewers.add(new AccessReviewStarted.Reviewer(account.email(), account.firstName(), count))));
        return reviewers;
    }

    private static RoleHolderSummary holder(AccessReviewItem item) {
        return new RoleHolderSummary(item.getAssignmentId(), item.getUserId(), item.getUserName(), item.getEmail(),
                item.getRoleId(), item.getBranchId(), item.getStartsAt(), item.getEndsAt());
    }

    private static AccessReviewResponse toResponse(AccessReview review, long itemCount, long decidedCount, long waitingCount) {
        return new AccessReviewResponse(review.getId(), review.getName(), review.getRoleId(), review.getBranchId(),
                review.getReviewerIds().stream().sorted().toList(), review.getDueAt(), review.getStatus().value(),
                review.getCreatedBy(), review.getCreatedAt(), review.getClosedAt(), review.getClosedBy(), itemCount,
                decidedCount, waitingCount);
    }

    private static AccessReviewItemResponse toResponse(AccessReviewItem item, boolean canDecide) {
        return new AccessReviewItemResponse(item.getId(), item.getAssignmentId(), item.getUserId(), item.getUserName(),
                item.getEmail(), item.getRoleId(), item.getRoleName(), item.getBranchId(), item.getStartsAt(), item.getEndsAt(),
                item.getDecision() == null ? null : item.getDecision().value(), item.getNote(), item.getDecidedBy(),
                item.getDecidedByName(), item.getDecidedAt(), item.getOutcome() == null ? null : item.getOutcome().value(),
                canDecide);
    }
}
