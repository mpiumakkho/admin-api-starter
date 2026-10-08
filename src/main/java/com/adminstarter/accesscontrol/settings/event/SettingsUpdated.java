package com.adminstarter.accesscontrol.settings.event;

import com.adminstarter.accesscontrol.common.dto.FieldChange;
import java.util.List;
import java.util.UUID;

public record SettingsUpdated(UUID actorId, List<FieldChange> changes) {}
