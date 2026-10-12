package com.adminstarter.accesscontrol.review.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Keep or remove each of these roles under review, with why */
public record AccessReviewDecisionRequest(
        @NotNull @Size(min = 1, max = 1000) List<@NotNull UUID> itemIds,
        @NotNull @Pattern(regexp = "keep|remove") String decision,
        @Nullable @Size(max = 500) String note) {}
