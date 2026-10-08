package com.adminstarter.accesscontrol.settings.service;

import com.adminstarter.accesscontrol.settings.dto.PasswordPolicyResponse;
import com.adminstarter.accesscontrol.settings.dto.SettingsResponse;
import java.util.UUID;

/** What other modules may read of the settings */
public interface SettingsService {

    SettingsResponse current();

    /**
     * Whether holders of this role must turn on two-step sign-in before anything else (twoFactorRoleIds, P-016). Asked
     * on every request: cached, evicted after commit whenever the settings change.
     */
    boolean requiresTwoFactor(UUID roleId);

    /** The password rules (P-017), which anyone may read, signed in or not: GET /api/auth/password-policy */
    PasswordPolicyResponse passwordPolicy();
}
