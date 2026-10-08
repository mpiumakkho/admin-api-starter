package com.adminstarter.accesscontrol.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** UserDraft: everything the server does not assign. Phone and photo may be left out. */
public record CreateUserRequest(
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @NotNull @Size(max = 254) @Pattern(regexp = EMAIL) String email,
        @Nullable @Size(max = 50) String phone,
        @NotBlank @Size(max = 100) String department,
        @NotNull UUID roleId,
        @NotNull @Pattern(regexp = STATUSES) String status,
        @Nullable @Size(max = AVATAR_MAX) String avatar) {

    /** The check admin-ng-starter makes: something@something.something, surrounding spaces allowed */
    public static final String EMAIL = "^\\s*[^\\s@]+@[^\\s@]+\\.[^\\s@]+\\s*$";
    public static final String STATUSES = "active|invited|suspended";
    /** A photo is a data URL; this leaves room for about 1.5 MB of image */
    public static final int AVATAR_MAX = 2_000_000;
}
