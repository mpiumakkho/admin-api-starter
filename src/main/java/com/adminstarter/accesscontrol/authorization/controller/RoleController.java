package com.adminstarter.accesscontrol.authorization.controller;

import com.adminstarter.accesscontrol.authorization.dto.CreateRoleRequest;
import com.adminstarter.accesscontrol.authorization.dto.RoleResponse;
import com.adminstarter.accesscontrol.authorization.dto.UpdateRoleRequest;
import com.adminstarter.accesscontrol.authorization.service.RoleManagementService;
import com.adminstarter.accesscontrol.common.context.Actor;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/roles")
public class RoleController {

    private final RoleManagementService roles;

    public RoleController(RoleManagementService roles) {
        this.roles = roles;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<RoleResponse> list() {
        return roles.list();
    }

    @PostMapping
    @PreAuthorize("hasAuthority('roles.manage')")
    public RoleResponse create(@AuthenticationPrincipal Actor actor, @Valid @RequestBody CreateRoleRequest request) {
        return roles.create(actor, request);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('roles.manage')")
    public RoleResponse update(@AuthenticationPrincipal Actor actor, @PathVariable UUID id,
            @Valid @RequestBody UpdateRoleRequest request) {
        return roles.update(actor, id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('roles.manage')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Actor actor, @PathVariable UUID id) {
        roles.delete(actor, id);
    }
}
