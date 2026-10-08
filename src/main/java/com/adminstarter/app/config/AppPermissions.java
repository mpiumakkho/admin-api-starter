package com.adminstarter.app.config;

import com.adminstarter.accesscontrol.authorization.spi.PermissionCatalog;
import com.adminstarter.accesscontrol.authorization.spi.PermissionDefinition;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * This project's own permissions, beside user management's (CorePermissions). None yet: add one per thing a role
 * may or may not do in a module of yours, such as {@code PermissionDefinition.of("invoices.view", "View invoices")},
 * and add the same key to the app's catalog.ts so the roles page offers it.
 */
@Component
public class AppPermissions implements PermissionCatalog {

    @Override
    public List<PermissionDefinition> permissions() {
        return List.of();
    }
}
