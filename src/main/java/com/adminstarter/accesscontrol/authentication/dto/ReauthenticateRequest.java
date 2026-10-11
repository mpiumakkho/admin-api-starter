package com.adminstarter.accesscontrol.authentication.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * POST /api/me/reauthenticate (P-023)
 *
 * @param code from the authenticator app, on the second try when two-step sign-in is on
 */
public record ReauthenticateRequest(
        @NotNull @Size(max = 1000) String password,
        @Nullable @Size(max = 20) String code) {}
