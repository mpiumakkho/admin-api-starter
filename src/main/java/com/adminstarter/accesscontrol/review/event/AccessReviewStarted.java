package com.adminstarter.accesscontrol.review.event;

import com.adminstarter.accesscontrol.common.dto.FieldChange;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Someone started an access review; it carries who may decide some of it, for the emails (P-028) */
public record AccessReviewStarted(UUID actorId, UUID reviewId, String name, List<FieldChange> changes, Instant dueAt,
        List<Reviewer> reviewers) {

    /** An active person who may decide some of it, and how many roles */
    public record Reviewer(String email, String firstName, int assignments) {}
}
