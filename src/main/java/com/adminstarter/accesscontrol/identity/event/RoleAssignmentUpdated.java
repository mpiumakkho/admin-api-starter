package com.adminstarter.accesscontrol.identity.event;

import com.adminstarter.accesscontrol.common.dto.FieldChange;
import java.util.List;
import java.util.UUID;

/** Someone moved the end of a person's role assignment, or changed its reason */
public record RoleAssignmentUpdated(UUID actorId, UUID userId, String fullName, List<FieldChange> changes) {}
