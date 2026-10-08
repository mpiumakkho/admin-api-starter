package com.adminstarter.accesscontrol.audit.dto;

import com.adminstarter.accesscontrol.common.dto.FieldChange;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Activity in admin-ng-starter's models.ts; targetId and changes are left out when there are none */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ActivityResponse(
        UUID id,
        Instant at,
        UUID actorId,
        String action,
        String targetLabel,
        @Nullable String targetId,
        String ip,
        String browser,
        String os,
        @Nullable List<FieldChange> changes) {}
