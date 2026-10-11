package com.adminstarter.accesscontrol.identity.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** A code from the authenticator app, or a recovery code (P-025) */
public record TwoFactorCodeRequest(@NotNull @Size(max = 20) String code) {}
