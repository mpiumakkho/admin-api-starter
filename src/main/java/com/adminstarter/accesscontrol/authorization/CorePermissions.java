package com.adminstarter.accesscontrol.authorization;

import com.adminstarter.accesscontrol.authorization.spi.PermissionCatalog;
import com.adminstarter.accesscontrol.authorization.spi.PermissionDefinition;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * The permissions user management itself checks. Same codes, order and meaning as PERMISSION_GROUPS in
 * admin-ng-starter's catalog.ts: change both together.
 */
@Component
public class CorePermissions implements PermissionCatalog {

    public static final String USERS_VIEW = "users.view";
    public static final String USERS_CREATE = "users.create";
    public static final String USERS_EDIT = "users.edit";
    public static final String USERS_DELETE = "users.delete";
    public static final String ROLES_VIEW = "roles.view";
    public static final String ROLES_MANAGE = "roles.manage";
    public static final String ACTIVITY_VIEW = "activity.view";
    public static final String ACTIVITY_EXPORT = "activity.export";
    public static final String SETTINGS_VIEW = "settings.view";
    public static final String SETTINGS_MANAGE = "settings.manage";
    public static final String BRANCHES_VIEW = "branches.view";
    public static final String BRANCHES_MANAGE = "branches.manage";

    /**
     * The four that a role limited to a branch grants over the people who belong to that branch, so a branch manager
     * looks after their branch's staff (P-024). The others come only from roles for the whole organization.
     */
    public static final Set<String> BRANCH_SCOPED = Set.of(USERS_VIEW, USERS_CREATE, USERS_EDIT, ACTIVITY_VIEW);

    @Override
    public List<PermissionDefinition> permissions() {
        return List.of(
                PermissionDefinition.of(USERS_VIEW, "View users"),
                PermissionDefinition.of(USERS_CREATE, "Add and invite users"),
                PermissionDefinition.of(USERS_EDIT, "Edit, suspend and reset users"),
                PermissionDefinition.of(USERS_DELETE, "Delete users"),
                PermissionDefinition.of(ROLES_VIEW, "View roles"),
                PermissionDefinition.of(ROLES_MANAGE, "Create and edit roles"),
                PermissionDefinition.of(ACTIVITY_VIEW, "View activity"),
                PermissionDefinition.of(ACTIVITY_EXPORT, "Export activity"),
                PermissionDefinition.of(SETTINGS_VIEW, "View settings"),
                PermissionDefinition.of(SETTINGS_MANAGE, "Change settings"),
                PermissionDefinition.of(BRANCHES_VIEW, "View branches"),
                PermissionDefinition.of(BRANCHES_MANAGE, "Add and change branches"));
    }
}
