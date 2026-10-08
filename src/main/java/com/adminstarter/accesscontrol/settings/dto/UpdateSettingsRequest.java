package com.adminstarter.accesscontrol.settings.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Partial&lt;AdminSettings&gt;: a field left out stays as it is */
public record UpdateSettingsRequest(
        @Nullable UUID defaultRoleId,
        @Nullable @Size(max = 100) List<@Size(max = 253) String> allowedDomains,
        @Nullable @Min(0) @Max(365) Integer inviteExpiryDays,
        @Nullable @Min(0) @Max(10080) Integer sessionTimeoutMinutes) {}
