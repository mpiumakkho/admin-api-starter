package com.adminstarter.accesscontrol.branch.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/** Partial&lt;BranchDraft&gt; and active: a field left out stays as it is; active false closes the branch */
public record UpdateBranchRequest(
        @Nullable @Pattern(regexp = CreateBranchRequest.CODE) String code,
        @Nullable @Size(min = 1, max = 100) @Pattern(regexp = ".*\\S.*") String name,
        @Nullable Boolean active) {}
