package com.adminstarter.accesscontrol.settings.dto;

import java.util.List;
import java.util.UUID;

/** AdminSettings in admin-ng-starter's models.ts */
public record SettingsResponse(
        UUID defaultRoleId,
        List<String> allowedDomains,
        int inviteExpiryDays,
        int sessionTimeoutMinutes,
        List<UUID> twoFactorRoleIds,
        int passwordMinLength,
        int passwordHistory,
        int activityRetentionDays,
        boolean branchesEnabled) {}
