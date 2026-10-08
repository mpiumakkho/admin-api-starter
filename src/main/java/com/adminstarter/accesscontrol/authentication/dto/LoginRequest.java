package com.adminstarter.accesscontrol.authentication.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * @param remember keep the session after the browser closes
 * @param code from the authenticator app, on the second try when two-step sign-in is on
 */
public record LoginRequest(
        @NotNull @Size(max = 254) String email,
        @NotNull @Size(max = 1000) String password,
        boolean remember,
        @Nullable @Size(max = 20) String code) {}
