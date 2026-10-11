package com.adminstarter.accesscontrol.branch.event;

import com.adminstarter.accesscontrol.common.dto.FieldChange;
import java.util.List;
import java.util.UUID;

public record BranchUpdated(UUID actorId, UUID branchId, String name, List<FieldChange> changes) {}
