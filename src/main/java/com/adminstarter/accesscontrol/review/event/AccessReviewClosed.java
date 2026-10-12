package com.adminstarter.accesscontrol.review.event;

import com.adminstarter.accesscontrol.common.dto.FieldChange;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** An access review closed, by someone or by the server at its due date (no actor), with how many were kept and removed */
public record AccessReviewClosed(@Nullable UUID actorId, UUID reviewId, String name, List<FieldChange> changes) {}
