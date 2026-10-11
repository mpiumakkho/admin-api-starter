package com.adminstarter.accesscontrol.authorization.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Partial&lt;RoleConflictDraft&gt;: a field left out stays as it is */
public record UpdateRoleConflictRequest(
        @Nullable @Size(min = 1, max = 100) @Pattern(regexp = ".*\\S.*") String name,
        @Nullable @Size(max = 500) String description,
        @Nullable @Size(min = 2, max = 20) List<@NotNull UUID> roleIds) {}
