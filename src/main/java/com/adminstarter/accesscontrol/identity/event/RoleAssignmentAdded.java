package com.adminstarter.accesscontrol.identity.event;

import com.adminstarter.accesscontrol.common.dto.FieldChange;
import java.util.List;
import java.util.UUID;

/** Someone gave a person a role (P-019): the role, and its start, end and reason when it has them */
public record RoleAssignmentAdded(UUID actorId, UUID userId, String fullName, List<FieldChange> changes, String reason) {}
