package com.adminstarter.accesscontrol.identity.entity;

import java.util.Locale;

public enum UserStatus {
    ACTIVE,
    /** Added with an invitation that has not been accepted yet */
    INVITED,
    SUSPENDED;

    /** As the API writes it: "active", "invited", "suspended" */
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static UserStatus of(String value) {
        return valueOf(value.toUpperCase(Locale.ROOT));
    }
}
