package com.adminstarter.accesscontrol.identity.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ImportUsersRequest(@NotNull @Size(max = 1000) List<@Valid @NotNull CreateUserRequest> users) {}
