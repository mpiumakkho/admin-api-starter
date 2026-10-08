package com.adminstarter.accesscontrol.authorization.spi;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * A role to create on the first start.
 *
 * @param code UPPER_SNAKE, how code finds the role again (the settings' default role, the first administrator);
 *     never shown and never checked for access
 * @param tone the badge color: primary, info, success, warning, danger or neutral
 * @param system built-in: can be edited but not deleted
 * @param permissions codes, or {@link #ALL_PERMISSIONS} for every permission there is
 */
public record RoleDefinition(String code, String name, String description, String tone, boolean system, Set<String> permissions) {

    public static final String ALL_PERMISSIONS = "*";
    private static final Pattern CODE = Pattern.compile("^[A-Z][A-Z0-9_]*$");

    public RoleDefinition {
        if (!CODE.matcher(code).matches()) {
            throw new IllegalArgumentException("Role code must be UPPER_SNAKE: " + code);
        }
        permissions = Set.copyOf(permissions);
    }

    public boolean grantsAll() {
        return permissions.contains(ALL_PERMISSIONS);
    }
}
