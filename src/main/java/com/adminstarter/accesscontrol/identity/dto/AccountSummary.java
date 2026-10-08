package com.adminstarter.accesscontrol.identity.dto;

import java.util.UUID;

/** What sign-in and the per-request check need to know about an account */
public record AccountSummary(UUID id, String email, String firstName, String fullName, String status, UUID roleId,
        boolean twoFactor) {

    public boolean active() {
        return "active".equals(status);
    }
}
