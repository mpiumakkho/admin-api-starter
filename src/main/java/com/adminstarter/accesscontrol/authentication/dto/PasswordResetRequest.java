package com.adminstarter.accesscontrol.authentication.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** "Forgot password?" */
public record PasswordResetRequest(@NotNull @Size(max = 254) String email) {}
