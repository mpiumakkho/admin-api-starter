package com.adminstarter.accesscontrol.identity.event;

import com.adminstarter.accesscontrol.common.dto.FieldChange;
import java.util.List;
import java.util.UUID;

public record UserDeleted(UUID actorId, UUID userId, String fullName, List<FieldChange> changes) {}
