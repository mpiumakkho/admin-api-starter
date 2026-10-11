package com.adminstarter.accesscontrol.identity.controller;

import com.adminstarter.accesscontrol.common.context.Actor;
import com.adminstarter.accesscontrol.identity.dto.CreateRoleAssignmentRequest;
import com.adminstarter.accesscontrol.identity.dto.ReasonRequest;
import com.adminstarter.accesscontrol.identity.dto.RoleAssignmentBatchRequest;
import com.adminstarter.accesscontrol.identity.dto.UpdateRoleAssignmentRequest;
import com.adminstarter.accesscontrol.identity.dto.UserResponse;
import com.adminstarter.accesscontrol.identity.service.RoleAssignmentService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Giving and taking away roles (API.md, "Role assignments"; P-019); each answers with the people it changed */
@RestController
@RequestMapping("/api/role-assignments")
public class RoleAssignmentController {

    private final RoleAssignmentService assignments;

    public RoleAssignmentController(RoleAssignmentService assignments) {
        this.assignments = assignments;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('users.edit')")
    public UserResponse assign(@AuthenticationPrincipal Actor actor, @Valid @RequestBody CreateRoleAssignmentRequest request) {
        return assignments.assign(actor, request);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('users.edit')")
    public UserResponse update(@AuthenticationPrincipal Actor actor, @PathVariable UUID id,
            @Valid @RequestBody UpdateRoleAssignmentRequest request) {
        return assignments.update(actor, id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('users.edit')")
    public UserResponse remove(@AuthenticationPrincipal Actor actor, @PathVariable UUID id,
            @Valid @RequestBody(required = false) @Nullable ReasonRequest request) {
        return assignments.remove(actor, id, ReasonRequest.of(request));
    }

    @PostMapping("/batch")
    @PreAuthorize("hasAuthority('users.edit')")
    public List<UserResponse> batch(@AuthenticationPrincipal Actor actor, @Valid @RequestBody RoleAssignmentBatchRequest request) {
        return assignments.batch(actor, request);
    }
}
