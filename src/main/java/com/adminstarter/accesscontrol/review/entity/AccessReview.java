package com.adminstarter.accesscontrol.review.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;
import org.jspecify.annotations.Nullable;

/**
 * A round of checking that people still need the roles they hold (P-028), as Microsoft Entra's access reviews: of one
 * role or every role, of the people of one branch or everyone, open until its due date. Its roles are
 * AccessReviewItems.
 */
@Entity
@Table(name = "access_reviews")
public class AccessReview {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    private String name;
    /** Null: every role */
    private @Nullable UUID roleId;
    /** Null: everyone; otherwise the branch whose people it covers */
    private @Nullable UUID branchId;

    /** People named to decide all of it, besides each person's administrators */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "access_review_reviewers", joinColumns = @JoinColumn(name = "review_id"))
    @Column(name = "user_id")
    private Set<UUID> reviewerIds = new HashSet<>();

    private Instant dueAt;
    @Enumerated(EnumType.STRING)
    private AccessReviewStatus status;
    /** Null when the person who started it has been deleted */
    private @Nullable UUID createdBy;
    private Instant createdAt;
    private @Nullable Instant closedAt;
    /** Null once closed: the server closed it at its due date */
    private @Nullable UUID closedBy;

    protected AccessReview() {}

    public AccessReview(String name, @Nullable UUID roleId, @Nullable UUID branchId, Set<UUID> reviewerIds, Instant dueAt,
            UUID createdBy, Instant now) {
        this.name = name;
        this.roleId = roleId;
        this.branchId = branchId;
        this.reviewerIds.addAll(reviewerIds);
        this.dueAt = dueAt;
        this.status = AccessReviewStatus.OPEN;
        this.createdBy = createdBy;
        this.createdAt = now;
    }

    /** By someone, or by the server at its due date (null) */
    public void close(@Nullable UUID closedBy, Instant now) {
        this.status = AccessReviewStatus.CLOSED;
        this.closedAt = now;
        this.closedBy = closedBy;
    }

    public boolean isOpen() {
        return status == AccessReviewStatus.OPEN;
    }

    public boolean names(UUID userId) {
        return reviewerIds.contains(userId);
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public @Nullable UUID getRoleId() {
        return roleId;
    }

    public @Nullable UUID getBranchId() {
        return branchId;
    }

    public Set<UUID> getReviewerIds() {
        return Set.copyOf(reviewerIds);
    }

    public Instant getDueAt() {
        return dueAt;
    }

    public AccessReviewStatus getStatus() {
        return status;
    }

    public @Nullable UUID getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public @Nullable Instant getClosedAt() {
        return closedAt;
    }

    public @Nullable UUID getClosedBy() {
        return closedBy;
    }
}
