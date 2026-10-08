package com.adminstarter.accesscontrol.authorization.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** RoleDraft: everything but the id and the system flag, which only the server sets */
public record CreateRoleRequest(
        @NotBlank @Size(max = 100) String name,
        @Nullable @Size(max = 500) String description,
        @Nullable @Pattern(regexp = TONES) String tone,
        @Nullable @Size(max = 200) List<String> permissions) {

    /** The badge colors a role can have (ROLE_TONES in catalog.ts) */
    public static final String TONES = "primary|info|success|warning|danger|neutral";
}
