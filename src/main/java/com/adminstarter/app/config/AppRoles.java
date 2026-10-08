package com.adminstarter.app.config;

import static com.adminstarter.accesscontrol.authorization.CorePermissions.ACTIVITY_EXPORT;
import static com.adminstarter.accesscontrol.authorization.CorePermissions.ACTIVITY_VIEW;
import static com.adminstarter.accesscontrol.authorization.CorePermissions.ROLES_VIEW;
import static com.adminstarter.accesscontrol.authorization.CorePermissions.SETTINGS_VIEW;
import static com.adminstarter.accesscontrol.authorization.CorePermissions.USERS_CREATE;
import static com.adminstarter.accesscontrol.authorization.CorePermissions.USERS_EDIT;
import static com.adminstarter.accesscontrol.authorization.CorePermissions.USERS_VIEW;

import com.adminstarter.accesscontrol.authorization.spi.RoleDefinition;
import com.adminstarter.accesscontrol.authorization.spi.RoleTemplate;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * The roles a new installation starts with besides Administrator: the same ones admin-ng-starter's sample data has.
 * They are created on the first start only; after that they belong to whoever manages roles on the dashboard.
 * VIEWER is the default role for new users (access-control.bootstrap.default-role).
 */
@Component
public class AppRoles implements RoleTemplate {

    @Override
    public List<RoleDefinition> roles() {
        return List.of(
                new RoleDefinition("MANAGER", "Manager", "Manages people in their teams and reads the activity log",
                        "info", true, Set.of(USERS_VIEW, USERS_CREATE, USERS_EDIT, ROLES_VIEW, ACTIVITY_VIEW,
                                ACTIVITY_EXPORT, SETTINGS_VIEW)),
                new RoleDefinition("SUPPORT", "Support", "Helps users with their accounts", "success", false,
                        Set.of(USERS_VIEW, USERS_EDIT, ROLES_VIEW, ACTIVITY_VIEW)),
                new RoleDefinition("VIEWER", "Viewer", "Read-only access to the user list", "neutral", false,
                        Set.of(USERS_VIEW, ROLES_VIEW)));
    }
}
