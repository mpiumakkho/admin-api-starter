package com.adminstarter.accesscontrol.identity.service;

import com.adminstarter.accesscontrol.AccessControlProperties;
import com.adminstarter.accesscontrol.authorization.CoreRoles;
import com.adminstarter.accesscontrol.authorization.service.RoleService;
import com.adminstarter.accesscontrol.identity.entity.RoleAssignment;
import com.adminstarter.accesscontrol.identity.entity.User;
import com.adminstarter.accesscontrol.identity.entity.UserStatus;
import com.adminstarter.accesscontrol.identity.repository.RoleAssignmentRepository;
import com.adminstarter.accesscontrol.identity.repository.UserRepository;
import com.adminstarter.accesscontrol.settings.service.SettingsService;
import java.time.Clock;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the first administrator when there are no users at all, from access-control.bootstrap.*: active, with the
 * Administrator role and the configured password, which must meet the password rules. Without one the application
 * does not start: a password made up here would have to be written somewhere, and a log is read by more people than
 * the one who runs the system (P-033).
 */
@Service
@Order(30)
public class AdminAccountInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminAccountInitializer.class);

    private final UserRepository users;
    private final RoleAssignmentRepository assignments;
    private final CredentialServiceImpl credentials;
    private final RoleService roles;
    private final SettingsService settings;
    private final AccessControlProperties properties;
    private final Clock clock;

    public AdminAccountInitializer(UserRepository users, RoleAssignmentRepository assignments,
            CredentialServiceImpl credentials, RoleService roles, SettingsService settings, AccessControlProperties properties,
            Clock clock) {
        this.users = users;
        this.assignments = assignments;
        this.credentials = credentials;
        this.roles = roles;
        this.settings = settings;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.count() > 0) {
            return;
        }
        var role = roles.findIdByCode(CoreRoles.ADMINISTRATOR)
                .orElseThrow(() -> new IllegalStateException("The Administrator role is missing"));
        var email = properties.bootstrap().adminEmail();
        var password = passwordFor(properties.bootstrap().adminPassword(), settings.current().passwordMinLength());
        var now = clock.instant();
        var admin = users.save(new User("Admin", "", email, "", "Operations", "", UserStatus.ACTIVE, now));
        // For good: someone must always be able to run the system (P-019)
        assignments.save(new RoleAssignment(admin.getId(), role, null, now, null, "", null, now));
        credentials.create(admin.getId(), password);
        log.info("Created the first administrator, {}", email);
    }

    /**
     * The first administrator's password as configured, once it meets the password rules; the start fails otherwise,
     * saying what to set, and never with the password in the message
     */
    static String passwordFor(@Nullable String configured, int minLength) {
        if (configured == null || configured.isBlank()) {
            throw new IllegalStateException("There are no users yet: set access-control.bootstrap.admin-password "
                    + "(ACCESS_CONTROL_BOOTSTRAP_ADMIN_PASSWORD) for the first administrator, then start again");
        }
        if (configured.length() < minLength) {
            throw new IllegalStateException("access-control.bootstrap.admin-password is shorter than the "
                    + minLength + " characters the password rules ask for");
        }
        return configured;
    }
}
