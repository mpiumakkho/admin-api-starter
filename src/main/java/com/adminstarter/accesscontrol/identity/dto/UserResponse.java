package com.adminstarter.accesscontrol.identity.dto;

import java.time.Instant;
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
        UUID roleId,
        String status,
        String avatar,
        Instant createdAt,
        @Nullable Instant lastActiveAt,
        boolean twoFactor) {}
