package com.adminstarter.accesscontrol.identity.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/** UserChanges: a field left out stays as it is; roles change through role assignments */
public record UpdateUserRequest(
        @Nullable @Size(max = 100) String firstName,
        @Nullable @Size(max = 100) String lastName,
        @Nullable @Size(max = 254) @Pattern(regexp = CreateUserRequest.EMAIL) String email,
        @Nullable @Size(max = 50) String phone,
        @Nullable @Size(max = 100) String department,
        @Nullable @Pattern(regexp = CreateUserRequest.STATUSES) String status,
        @Nullable @Size(max = CreateUserRequest.AVATAR_MAX) String avatar) {}
