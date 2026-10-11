package com.adminstarter.accesscontrol.authorization.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** RoleConflictDraft: a name, maybe a description, and two to twenty different roles */
public record CreateRoleConflictRequest(
        @NotBlank @Size(max = 100) String name,
        @Nullable @Size(max = 500) String description,
        @NotNull @Size(min = 2, max = 20) List<@NotNull UUID> roleIds) {}
