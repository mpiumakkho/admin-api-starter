package com.adminstarter.accesscontrol.authentication.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * UserSession in admin-ng-starter's models.ts
 *
 * @param current the session the request came from
 */
public record UserSessionResponse(UUID id, UUID userId, Instant createdAt, Instant lastSeenAt, String ip, String browser,
        String os, boolean current) {}
