package com.adminstarter.accesscontrol.authorization.event;

import com.adminstarter.accesscontrol.common.dto.FieldChange;
import java.util.List;
import java.util.UUID;

public record RoleDeleted(UUID actorId, UUID roleId, String name, List<FieldChange> changes) {}
