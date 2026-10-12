package com.adminstarter.accesscontrol.review.event;

import com.adminstarter.accesscontrol.common.dto.FieldChange;
import java.util.List;
import java.util.UUID;

/** Someone decided to keep or remove roles under review; nothing changes until it closes */
public record AccessReviewDecided(UUID actorId, UUID reviewId, String name, List<FieldChange> changes, String note) {}
