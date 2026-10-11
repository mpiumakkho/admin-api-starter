package com.adminstarter.accesscontrol.identity.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** User in admin-ng-starter's models.ts */
public record UserResponse(
        UUID id,
        String firstName,
        String lastName,
        String email,
        String phone,
        String department,
        @Nullable UUID branchId,
        List<RoleAssignmentResponse> roles,
        String status,
        String avatar,
        Instant createdAt,
        @Nullable Instant lastActiveAt,
        boolean twoFactor) {}
