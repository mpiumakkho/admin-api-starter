package com.adminstarter.accesscontrol.identity.event;

import com.adminstarter.accesscontrol.common.dto.FieldChange;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Someone took a role away from a person, or an access review did when it closed (no actor, P-028); it ended at once */
public record RoleAssignmentRemoved(@Nullable UUID actorId, UUID userId, String fullName, List<FieldChange> changes, String reason) {}
