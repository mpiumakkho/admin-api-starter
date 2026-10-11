package com.adminstarter.accesscontrol.identity.service;

import com.adminstarter.accesscontrol.AccessControlProperties;
import com.adminstarter.accesscontrol.authorization.CoreRoles;
import com.adminstarter.accesscontrol.authorization.service.RoleService;
import com.adminstarter.accesscontrol.identity.entity.RoleAssignment;
import com.adminstarter.accesscontrol.identity.entity.User;
import com.adminstarter.accesscontrol.identity.entity.UserStatus;
import com.adminstarter.accesscontrol.identity.repository.RoleAssignmentRepository;
import com.adminstarter.accesscontrol.identity.repository.UserRepository;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the first administrator when there are no users at all, from access-control.bootstrap.*: active, with the
 * Administrator role and the configured password, or a random one written to the log once.
 */
@Service
@Order(30)
public class AdminAccountInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminAccountInitializer.class);

    private final UserRepository users;
    private final RoleAssignmentRepository assignments;
    private final CredentialServiceImpl credentials;
    private final RoleService roles;
    private final AccessControlProperties properties;
    private final Clock clock;

    public AdminAccountInitializer(UserRepository users, RoleAssignmentRepository assignments,
            CredentialServiceImpl credentials, RoleService roles, AccessControlProperties properties, Clock clock) {
        this.users = users;
        this.assignments = assignments;
        this.credentials = credentials;
        this.roles = roles;
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
        var configured = properties.bootstrap().adminPassword();
        var password = configured == null || configured.isBlank() ? randomPassword() : configured;
        var now = clock.instant();
        var admin = users.save(new User("Admin", "", email, "", "Operations", "", UserStatus.ACTIVE, now));
        // For good: someone must always be able to run the system (P-019)
        assignments.save(new RoleAssignment(admin.getId(), role, null, now, null, "", null, now));
        credentials.create(admin.getId(), password);
        if (password.equals(configured)) {
            log.info("Created the first administrator, {}", email);
        } else {
            log.warn("Created the first administrator, {}, with the password {} — change it after signing in, "
                    + "or set access-control.bootstrap.admin-password before the first start", email, password);
        }
    }

    private static String randomPassword() {
        var bytes = new byte[12];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
