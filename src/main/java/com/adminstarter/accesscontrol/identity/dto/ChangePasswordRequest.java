package com.adminstarter.accesscontrol.identity.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotNull @Size(max = 1000) String currentPassword,
        @NotNull @Size(max = 1000) String newPassword) {}
