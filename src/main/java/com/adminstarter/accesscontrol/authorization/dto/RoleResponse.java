package com.adminstarter.accesscontrol.authorization.dto;

import java.util.List;
import java.util.UUID;

/** Role in admin-ng-starter's models.ts; permissions in catalog order */
public record RoleResponse(UUID id, String name, String description, String tone, List<String> permissions, boolean system) {}
