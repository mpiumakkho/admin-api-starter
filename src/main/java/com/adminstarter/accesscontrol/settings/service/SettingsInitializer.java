package com.adminstarter.accesscontrol.settings.service;

import com.adminstarter.accesscontrol.AccessControlProperties;
import com.adminstarter.accesscontrol.authorization.service.RoleService;
import com.adminstarter.accesscontrol.settings.entity.AdminSettings;
import com.adminstarter.accesscontrol.settings.repository.AdminSettingsRepository;
import java.time.Clock;
import java.util.List;
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
        var code = properties.bootstrap().defaultRole();
        var defaultRole = roles.findIdByCode(code)
                .orElseThrow(() -> new IllegalStateException("access-control.bootstrap.default-role names no role: " + code));
        settings.save(new AdminSettings(defaultRole, List.of(), INVITE_EXPIRY_DAYS, SESSION_TIMEOUT_MINUTES, List.of(),
                PASSWORD_MIN_LENGTH, PASSWORD_HISTORY, ACTIVITY_RETENTION_DAYS,
                clock.instant()));
    }
}
