package com.adminstarter.accesscontrol.settings.service;

import com.adminstarter.accesscontrol.authorization.event.RoleDeleted;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * What deleting a role means for the settings (API.md, "Settings"): it comes off twoFactorRoleIds. Runs in the
 * transaction of the delete, so a delete that is refused (role_in_use, default_role) leaves the list as it was. The
 * role.deleted entry is the activity for it, as in local-admin-api.ts: no settings.updated entry of its own.
 */
@Service
public class RoleEventListener {

    private final SettingsServiceImpl settings;

    public RoleEventListener(SettingsServiceImpl settings) {
        this.settings = settings;
    }

    @EventListener
    void on(RoleDeleted event) {
        settings.forgetRole(event.roleId());
    }
}
