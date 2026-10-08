package com.adminstarter.accesscontrol.identity.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record DeleteUsersRequest(@NotNull @Size(max = 1000) List<@NotNull UUID> ids) {}
