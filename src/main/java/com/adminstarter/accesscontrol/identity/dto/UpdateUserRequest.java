package com.adminstarter.accesscontrol.identity.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * UserChanges: a field left out stays as it is; roles change through role assignments. {@code branchId} and
 * {@code language} must tell the two apart, as UpdateRoleAssignmentRequest's endsAt does: left out, {@link #branchId()}
 * is null; {@code "branchId": null} (no branch) is an empty Optional. Likewise {@code "language": null} gives the
 * settings' language back.
 */
public final class UpdateUserRequest {

    @JsonProperty
    @Size(max = 100)
    @Pattern(regexp = CreateUserRequest.NOT_BLANK)
    private @Nullable String firstName;

    @JsonProperty
    @Size(max = 100)
    @Pattern(regexp = CreateUserRequest.NOT_BLANK)
    private @Nullable String lastName;

    @JsonProperty
    @Size(max = 254)
    @Pattern(regexp = CreateUserRequest.EMAIL)
    private @Nullable String email;

    @JsonProperty
    @Size(max = 50)
    private @Nullable String phone;

    @JsonProperty
    @Size(max = 100)
    @Pattern(regexp = CreateUserRequest.NOT_BLANK)
    private @Nullable String department;

    private boolean branchIdSent;
    private @Nullable UUID branchId;

    @JsonProperty
    @Pattern(regexp = CreateUserRequest.STATUSES)
    private @Nullable String status;

    @JsonProperty
    @CreateUserRequest.Avatar
    private @Nullable String avatar;

    private boolean languageSent;
    @Pattern(regexp = CreateUserRequest.LANGUAGES)
    private @Nullable String language;

    @JsonSetter("branchId")
    void readBranchId(@Nullable UUID value) {
        branchIdSent = true;
        branchId = value;
    }

    @JsonSetter("language")
    void readLanguage(@Nullable String value) {
        languageSent = true;
        language = value;
    }

    public @Nullable String firstName() {
        return firstName;
    }

    public @Nullable String lastName() {
        return lastName;
    }

    public @Nullable String email() {
        return email;
    }

    public @Nullable String phone() {
        return phone;
    }

    public @Nullable String department() {
        return department;
    }

    /** Null when left out; empty for no branch; or the branch */
    public @Nullable Optional<UUID> branchId() {
        return branchIdSent ? Optional.ofNullable(branchId) : null;
    }

    public @Nullable String status() {
        return status;
    }

    public @Nullable String avatar() {
        return avatar;
    }

    /** Null when left out; empty for the settings' language; or "th" or "en" (P-029) */
    public @Nullable Optional<String> language() {
        return languageSent ? Optional.ofNullable(language) : null;
    }
}
