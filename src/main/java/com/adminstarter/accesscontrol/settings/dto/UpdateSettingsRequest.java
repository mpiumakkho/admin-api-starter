package com.adminstarter.accesscontrol.settings.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Partial&lt;AdminSettings&gt;: a field left out stays as it is. A value outside its range is invalid_request. */
public record UpdateSettingsRequest(
        @Nullable UUID defaultRoleId,
        @Nullable @Size(max = 100) List<@Size(max = 253) String> allowedDomains,
        @Nullable @Min(0) @Max(365) Integer inviteExpiryDays,
        @Nullable @Min(0) @Max(10080) Integer sessionTimeoutMinutes,
        @Nullable @Size(max = 100) List<@NotNull UUID> twoFactorRoleIds,
        @Nullable @Min(8) @Max(64) Integer passwordMinLength,
        @Nullable @Min(0) @Max(24) Integer passwordHistory,
        @Nullable @Min(90) @Max(3650) Integer activityRetentionDays,
        @Nullable Boolean branchesEnabled,
        @Nullable @Size(max = 10) List<@NotNull @Pattern(regexp = REASON_ACTIONS) String> reasonsRequired,
        @Nullable @Min(0) @Max(1440) Integer reauthMinutes,
        @Nullable @Min(0) @Max(3650) Integer suspendInactiveDays) {

    /** The changes that can need a reason (ReasonAction in admin-ng-starter's models.ts, P-021) */
    public static final String REASON_ACTIONS = "status|delete|removeRole|resetSignIn|endSessions";
}
