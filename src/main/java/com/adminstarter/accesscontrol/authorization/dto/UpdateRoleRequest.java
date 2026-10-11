package com.adminstarter.accesscontrol.authorization.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Partial&lt;RoleDraft&gt;: a field left out stays as it is. A built-in role changes only its tone and assignment rules. */
public record UpdateRoleRequest(
        @Nullable @Size(min = 1, max = 100) @Pattern(regexp = ".*\\S.*") String name,
        @Nullable @Size(max = 500) String description,
        @Nullable @Pattern(regexp = CreateRoleRequest.TONES) String tone,
        @Nullable @Size(max = 200) List<String> permissions,
        @Valid CreateRoleRequest.@Nullable Assignment assignment) {}
