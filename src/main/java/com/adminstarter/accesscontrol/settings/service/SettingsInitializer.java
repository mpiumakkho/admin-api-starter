package com.adminstarter.accesscontrol.settings.service;

import com.adminstarter.accesscontrol.AccessControlProperties;
import com.adminstarter.accesscontrol.authorization.service.RoleService;
import com.adminstarter.accesscontrol.settings.entity.AdminSettings;
import com.adminstarter.accesscontrol.settings.repository.AdminSettingsRepository;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates the settings of a new installation (DEFAULT_SETTINGS in catalog.ts), once the roles exist */
@Service
@Order(20)
public class SettingsInitializer implements ApplicationRunner {

    private static final int INVITE_EXPIRY_DAYS = 7;
    /** Signed out after half an hour without activity, as NIST SP 800-63B asks of AAL2 (P-037) */
    private static final int SESSION_TIMEOUT_MINUTES = 30;
    private static final int PASSWORD_MIN_LENGTH = 8;
    private static final int PASSWORD_HISTORY = 0;
    private static final int ACTIVITY_RETENTION_DAYS = 730;

    private final AdminSettingsRepository settings;
    private final RoleService roles;
    private final AccessControlProperties properties;
    private final Clock clock;

    public SettingsInitializer(AdminSettingsRepository settings, RoleService roles, AccessControlProperties properties,
            Clock clock) {
        this.settings = settings;
        this.roles = roles;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (settings.existsById(AdminSettings.ID)) {
            return;
        }
        var bootstrap = properties.bootstrap();
        var defaultRole = role(bootstrap.defaultRole(), "default-role");
        // Two-step sign-in for Administrator from the start (P-042): the first administrator turns it on at their first
        // sign-in, before anything else opens
        var twoFactorRoles = bootstrap.twoFactorRoles().stream().map(code -> role(code, "two-factor-roles")).toList();
        settings.save(new AdminSettings(defaultRole, List.of(), INVITE_EXPIRY_DAYS, SESSION_TIMEOUT_MINUTES, twoFactorRoles,
                PASSWORD_MIN_LENGTH, PASSWORD_HISTORY, ACTIVITY_RETENTION_DAYS,
                clock.instant()));
    }

    private UUID role(String code, String property) {
        return roles.findIdByCode(code)
                .orElseThrow(() -> new IllegalStateException("access-control.bootstrap." + property + " names no role: " + code));
    }
}
