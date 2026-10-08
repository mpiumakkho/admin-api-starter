package com.adminstarter.accesscontrol.authentication.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Accepting an invitation, or confirming a password reset: the token from the link and the new password */
public record EmailLinkRequest(@NotNull @Size(max = 100) String token, @NotNull @Size(max = 1000) String password) {}
