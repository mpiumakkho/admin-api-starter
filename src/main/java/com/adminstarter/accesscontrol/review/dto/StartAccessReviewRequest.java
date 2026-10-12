package com.adminstarter.accesscontrol.review.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** AccessReviewDraft: a name, the role (null: every role), the branch whose people (null: everyone), when it closes */
public record StartAccessReviewRequest(
        @NotBlank @Size(max = 100) String name,
        @Nullable UUID roleId,
        @Nullable UUID branchId,
        @NotNull Instant dueAt,
        @Nullable @Size(max = 20) List<@NotNull UUID> reviewerIds) {}
