package com.adminstarter.accesscontrol.authorization.spi;

import java.util.regex.Pattern;

/**
 * One permission a catalog declares, such as {@code users.view}: {@code <resource>.<action>}, the form
 * admin-ng-starter's catalog.ts uses.
 */
public record PermissionDefinition(String code, String description) {

    public static final Pattern CODE = Pattern.compile("^[a-z][a-z0-9-]*\\.[a-z][a-z0-9-]*$");

    public PermissionDefinition {
        if (!CODE.matcher(code).matches()) {
            throw new IllegalArgumentException("Permission code must look like resource.action: " + code);
        }
    }

    public static PermissionDefinition of(String code, String description) {
        return new PermissionDefinition(code, description);
    }

    public String resource() {
        return code.substring(0, code.indexOf('.'));
    }

    public String action() {
        return code.substring(code.indexOf('.') + 1);
    }
}
