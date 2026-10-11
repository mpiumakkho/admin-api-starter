package com.adminstarter.accesscontrol.authorization.dto;

import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Role in admin-ng-starter's models.ts; permissions in catalog order */
public record RoleResponse(UUID id, String name, String description, String tone, List<String> permissions,
        Assignment assignment, boolean system) {

    /** RoleAssignmentRules: how the role may be given; maxDays null is no limit */
    public record Assignment(@Nullable Integer maxDays, boolean reasonRequired) {}
}
