package com.adminstarter.accesscontrol.identity.event;

import com.adminstarter.accesscontrol.common.dto.FieldChange;
import java.util.List;
import java.util.UUID;

/** Someone took a role away from a person; it ended at once */
public record RoleAssignmentRemoved(UUID actorId, UUID userId, String fullName, List<FieldChange> changes, String reason) {}
