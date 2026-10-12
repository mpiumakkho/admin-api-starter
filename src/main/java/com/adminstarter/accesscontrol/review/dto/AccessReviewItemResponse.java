package com.adminstarter.accesscontrol.review.dto;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * AccessReviewItem in admin-ng-starter's models.ts: decision "keep" or "remove", outcome once closed ("kept",
 * "removed", "not_reviewed", "gone", "last_administrator"), and whether the person asking may decide it now
 */
public record AccessReviewItemResponse(UUID id, UUID assignmentId, UUID userId, String userName, String email, UUID roleId,
        String roleName, @Nullable UUID branchId, Instant startsAt, @Nullable Instant endsAt, @Nullable String decision,
        String note, @Nullable UUID decidedById, String decidedByName, @Nullable Instant decidedAt, @Nullable String outcome,
        boolean canDecide) {}
