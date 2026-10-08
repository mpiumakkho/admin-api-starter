package com.adminstarter.accesscontrol.settings.service;

import com.adminstarter.accesscontrol.authorization.event.RoleDeleted;
import com.adminstarter.accesscontrol.settings.entity.AdminSettings;
import com.adminstarter.accesscontrol.settings.repository.AdminSettingsRepository;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * What deleting a role means for the settings (API.md, "Settings"): it comes off twoFactorRoleIds. Runs in the
 * transaction of the delete, so a delete that is refused (role_in_use, default_role) leaves the list as it was. The
 * role.deleted entry is the activity for it, as in local-admin-api.ts: no settings.updated entry of its own.
 */
@Service
public class RoleEventListener {

    private final AdminSettingsRepository settings;
    private final Clock clock;

    public RoleEventListener(AdminSettingsRepository settings, Clock clock) {
        this.settings = settings;
        this.clock = clock;
    }

    @EventListener
    void on(RoleDeleted event) {
        settings.findById(AdminSettings.ID)
                .ifPresent(current -> current.removeTwoFactorRole(event.roleId(), clock.instant().truncatedTo(ChronoUnit.MILLIS)));
    }
}
