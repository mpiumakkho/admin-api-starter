package com.adminstarter.accesscontrol.branch.dto;

import java.time.Instant;
import java.util.UUID;

/** Branch in admin-ng-starter's models.ts */
public record BranchResponse(UUID id, String code, String name, boolean active, Instant createdAt) {}
