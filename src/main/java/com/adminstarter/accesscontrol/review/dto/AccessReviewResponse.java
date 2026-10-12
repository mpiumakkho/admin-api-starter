package com.adminstarter.accesscontrol.review.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * AccessReview in admin-ng-starter's models.ts: status "open" or "closed"; waitingCount is how many roles the person
 * asking may decide that have no decision yet
 */
public record AccessReviewResponse(UUID id, String name, @Nullable UUID roleId, @Nullable UUID branchId, List<UUID> reviewerIds,
        Instant dueAt, String status, @Nullable UUID createdById, Instant createdAt, @Nullable Instant closedAt,
        @Nullable UUID closedById, long itemCount, long decidedCount, long waitingCount) {}
