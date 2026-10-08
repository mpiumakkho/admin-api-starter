package com.adminstarter.accesscontrol.identity.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** POST /users/role: the same role for several people at once */
public record SetUserRoleRequest(
        @NotNull @Size(max = 1000) List<@NotNull UUID> ids,
        @NotNull UUID roleId) {}
