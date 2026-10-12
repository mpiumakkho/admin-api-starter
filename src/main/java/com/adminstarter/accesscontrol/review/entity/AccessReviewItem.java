package com.adminstarter.accesscontrol.review.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;
import org.jspecify.annotations.Nullable;

/**
 * One role assignment under review (P-028). The person, the role and the dates are kept as they were when the review
 * started, as text and ids with no foreign keys, so the report reads the same after they change or are deleted.
 */
@Entity
@Table(name = "access_review_items")
public class AccessReviewItem {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    private UUID reviewId;
    private UUID assignmentId;
    private UUID userId;
    private String userName;
    private String email;
    private UUID roleId;
    private String roleName;
    /** The assignment's scope: null for the whole organization */
    private @Nullable UUID branchId;
    private Instant startsAt;
    private @Nullable Instant endsAt;

    @Enumerated(EnumType.STRING)
    private @Nullable AccessReviewDecision decision;
    private String note;
    private @Nullable UUID decidedBy;
    private String decidedByName;
    private @Nullable Instant decidedAt;
    @Enumerated(EnumType.STRING)
    private @Nullable AccessReviewOutcome outcome;

    protected AccessReviewItem() {}

    public AccessReviewItem(UUID reviewId, UUID assignmentId, UUID userId, String userName, String email, UUID roleId,
            String roleName, @Nullable UUID branchId, Instant startsAt, @Nullable Instant endsAt) {
        this.reviewId = reviewId;
        this.assignmentId = assignmentId;
        this.userId = userId;
        this.userName = userName;
        this.email = email;
        this.roleId = roleId;
        this.roleName = roleName;
        this.branchId = branchId;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.note = "";
        this.decidedByName = "";
    }

    /** The latest decision counts, with who made it and when */
    public void decide(AccessReviewDecision decision, String note, UUID decidedBy, String decidedByName, Instant now) {
        this.decision = decision;
        this.note = note;
        this.decidedBy = decidedBy;
        this.decidedByName = decidedByName;
        this.decidedAt = now;
    }

    /** What closing the review did with it */
    public void settle(AccessReviewOutcome outcome) {
        this.outcome = outcome;
    }

    public UUID getId() {
        return id;
    }

    public UUID getReviewId() {
        return reviewId;
    }

    public UUID getAssignmentId() {
        return assignmentId;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getUserName() {
        return userName;
    }

    public String getEmail() {
        return email;
    }

    public UUID getRoleId() {
        return roleId;
    }

    public String getRoleName() {
        return roleName;
    }

    public @Nullable UUID getBranchId() {
        return branchId;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public @Nullable Instant getEndsAt() {
        return endsAt;
    }

    public @Nullable AccessReviewDecision getDecision() {
        return decision;
    }

    public String getNote() {
        return note;
    }

    public @Nullable UUID getDecidedBy() {
        return decidedBy;
    }

    public String getDecidedByName() {
        return decidedByName;
    }

    public @Nullable Instant getDecidedAt() {
        return decidedAt;
    }

    public @Nullable AccessReviewOutcome getOutcome() {
        return outcome;
    }
}
