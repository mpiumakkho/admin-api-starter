package com.adminstarter.accesscontrol.authorization.dto;

import java.util.List;
import java.util.UUID;

/** RoleConflict in admin-ng-starter's models.ts; roles oldest first, as GET /roles lists them */
public record RoleConflictResponse(UUID id, String name, String description, List<UUID> roleIds) {}
