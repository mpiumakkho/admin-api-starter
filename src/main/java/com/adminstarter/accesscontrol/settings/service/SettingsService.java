package com.adminstarter.accesscontrol.settings.service;

import com.adminstarter.accesscontrol.settings.dto.PasswordPolicyResponse;
import com.adminstarter.accesscontrol.settings.dto.SettingsResponse;
import java.time.Duration;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** What other modules may read of the settings */
public interface SettingsService {

    SettingsResponse current();

    /**
     * Whether holders of this role must turn on two-step sign-in before anything else (twoFactorRoleIds, P-016). Asked
     * on every request: cached, evicted after commit whenever the settings change.
     */
    boolean requiresTwoFactor(UUID roleId);

    /**
     * How long after signing in, or confirming their password, someone can make the risky changes (reauthMinutes,
     * P-023); zero never asks. Asked on every request: cached, evicted after commit whenever the settings change.
     */
    Duration reauthWindow();

    /**
     * How long a session may go without a request before the server ends it (sessionTimeoutMinutes, P-027); zero never.
     * Asked on every request: cached, evicted after commit whenever the settings change.
     */
    Duration idleTimeout();

    /** The password rules (P-017), which anyone may read, signed in or not: GET /api/auth/password-policy */
    PasswordPolicyResponse passwordPolicy();

    /**
     * The reason a change gives, trimmed, for the activity log (P-021); reason_required when the settings' reasonsRequired
     * lists the action ("status", "delete", "removeRole", "resetSignIn" or "endSessions") and it is blank
     */
    String requireReason(String action, @Nullable String reason);
}
