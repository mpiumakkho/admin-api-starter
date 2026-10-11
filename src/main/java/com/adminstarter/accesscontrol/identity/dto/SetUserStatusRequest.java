package com.adminstarter.accesscontrol.identity.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record SetUserStatusRequest(
        @NotNull @Size(max = 1000) List<@NotNull UUID> ids,
        @NotNull @Pattern(regexp = CreateUserRequest.STATUSES) String status,
        @Nullable @Size(max = RoleGrantRequest.MAX_REASON) String reason) {}
