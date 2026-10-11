package com.adminstarter.accesscontrol.identity.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;
import org.jspecify.annotations.Nullable;

/**
 * One role given to one person, from a start until an end or for good (P-019): NIST RBAC's user assignment, with the
 * start and end of Microsoft Entra's role assignments. It counts while {@code startsAt <= now < endsAt}, for the whole
 * organization or, as an Azure RBAC scope, for one branch (P-020).
 */
@Entity
@Table(name = "role_assignments")
public class RoleAssignment {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    private UUID userId;
    private UUID roleId;
    /** Null: the whole organization; otherwise the branch it is limited to (P-020) */
    private @Nullable UUID branchId;
    private Instant startsAt;
    /** Null: for good */
    private @Nullable Instant endsAt;
    private String reason;
    /** Null when the server gave it, or the person who did has been deleted */
    private @Nullable UUID assignedBy;
    private Instant assignedAt;
    /** When RoleAssignmentExpiryJob logged its end; null until then */
    private @Nullable Instant expiryLoggedAt;

    protected RoleAssignment() {}

    public RoleAssignment(UUID userId, UUID roleId, @Nullable UUID branchId, Instant startsAt, @Nullable Instant endsAt,
            String reason, @Nullable UUID assignedBy, Instant now) {
        this.userId = userId;
        this.roleId = roleId;
        this.branchId = branchId;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.reason = reason;
        this.assignedBy = assignedBy;
        this.assignedAt = now;
    }

    /** Extends, shortens or renews it; a renewed one is logged again when it ends */
    public void moveEnd(@Nullable Instant endsAt) {
        this.endsAt = endsAt;
        this.expiryLoggedAt = null;
    }

    public void changeReason(String reason) {
        this.reason = reason;
    }

    /** Whether it counts at this moment */
    public boolean activeAt(Instant now) {
        return !startsAt.isAfter(now) && (endsAt == null || endsAt.isAfter(now));
    }

    /** Whether it counts now or will later: whoever manages the person must be able to give it */
    public boolean heldAt(Instant now) {
        return endsAt == null || endsAt.isAfter(now);
    }

    /** Whether it would count at some moment along with [start, end) */
    public boolean overlaps(Instant start, @Nullable Instant end) {
        return (end == null || startsAt.isBefore(end)) && (endsAt == null || start.isBefore(endsAt));
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getRoleId() {
        return roleId;
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

    public String getReason() {
        return reason;
    }

    public @Nullable UUID getAssignedBy() {
        return assignedBy;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }
}
