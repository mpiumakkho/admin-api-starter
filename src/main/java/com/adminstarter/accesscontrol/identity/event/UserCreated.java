package com.adminstarter.accesscontrol.identity.event;

import com.adminstarter.accesscontrol.common.dto.FieldChange;
import java.util.List;
import java.util.UUID;

/** Someone added a user; with {@code invited}, the user gets an invitation email */
public record UserCreated(UUID actorId, UUID userId, String fullName, String email, String firstName, boolean invited,
        List<FieldChange> changes) {}
