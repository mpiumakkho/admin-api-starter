package com.adminstarter.accesscontrol.branch.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** BranchDraft: a code of 1 to 10 letters, digits or dashes (surrounding spaces allowed), and a name */
public record CreateBranchRequest(
        @NotNull @Pattern(regexp = CODE) String code,
        @NotBlank @Size(max = 100) String name) {

    /** BRANCH_CODE in local-admin-api.ts, with the spaces the server trims */
    public static final String CODE = "^\\s*[A-Za-z0-9-]{1,10}\\s*$";
}
