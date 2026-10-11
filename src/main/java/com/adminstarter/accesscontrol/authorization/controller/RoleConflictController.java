package com.adminstarter.accesscontrol.authorization.controller;

import com.adminstarter.accesscontrol.authorization.dto.CreateRoleConflictRequest;
import com.adminstarter.accesscontrol.authorization.dto.RoleConflictResponse;
import com.adminstarter.accesscontrol.authorization.dto.UpdateRoleConflictRequest;
import com.adminstarter.accesscontrol.authorization.service.RoleConflictService;
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
@RequestMapping("/api/role-conflicts")
public class RoleConflictController {

    private final RoleConflictService conflicts;

    public RoleConflictController(RoleConflictService conflicts) {
        this.conflicts = conflicts;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<RoleConflictResponse> list() {
        return conflicts.list();
    }

    @PostMapping
    @PreAuthorize("hasAuthority('roles.manage')")
    public RoleConflictResponse create(@AuthenticationPrincipal Actor actor, @Valid @RequestBody CreateRoleConflictRequest request) {
        return conflicts.create(actor, request);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('roles.manage')")
    public RoleConflictResponse update(@AuthenticationPrincipal Actor actor, @PathVariable UUID id,
            @Valid @RequestBody UpdateRoleConflictRequest request) {
        return conflicts.update(actor, id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('roles.manage')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Actor actor, @PathVariable UUID id) {
        conflicts.delete(actor, id);
    }
}
