package com.adminstarter.accesscontrol.review.event;

import java.util.UUID;

/** Someone deleted an open access review; nobody's roles changed */
public record AccessReviewDeleted(UUID actorId, String name) {}
