package com.adminstarter.accesscontrol.identity.event;

import com.adminstarter.accesscontrol.common.dto.FieldChange;
import java.util.List;
import java.util.UUID;

public record UserUpdated(UUID actorId, UUID userId, String fullName, List<FieldChange> changes) {}
