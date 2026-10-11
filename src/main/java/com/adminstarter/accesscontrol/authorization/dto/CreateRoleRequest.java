package com.adminstarter.accesscontrol.authorization.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * RoleDraft: everything but the id and the system flag, which only the server sets. Without assignment rules, the role
 * may be given for good, without a reason.
 */
public record CreateRoleRequest(
        @NotBlank @Size(max = 100) String name,
        @Nullable @Size(max = 500) String description,
        @Nullable @Pattern(regexp = TONES) String tone,
        @Nullable @Size(max = 200) List<String> permissions,
        @Nullable @Valid Assignment assignment) {

    /** The badge colors a role can have (ROLE_TONES in catalog.ts) */
    public static final String TONES = "primary|info|success|warning|danger|neutral";

    /** RoleAssignmentRules: an end within maxDays (1 to 365, or null for no limit), and whether a reason is required */
    public record Assignment(@Nullable @Min(1) @Max(365) Integer maxDays, @NotNull Boolean reasonRequired) {}
}
