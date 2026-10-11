package com.adminstarter.accesscontrol.branch.controller;

import com.adminstarter.accesscontrol.branch.dto.BranchResponse;
import com.adminstarter.accesscontrol.branch.dto.CreateBranchRequest;
import com.adminstarter.accesscontrol.branch.dto.UpdateBranchRequest;
import com.adminstarter.accesscontrol.branch.service.BranchManagementService;
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
@RequestMapping("/api/branches")
public class BranchController {

    private final BranchManagementService branches;

    public BranchController(BranchManagementService branches) {
        this.branches = branches;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<BranchResponse> list() {
        return branches.list();
    }

    @PostMapping
    @PreAuthorize("hasAuthority('branches.manage')")
    public BranchResponse create(@AuthenticationPrincipal Actor actor, @Valid @RequestBody CreateBranchRequest request) {
        return branches.create(actor, request);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('branches.manage')")
    public BranchResponse update(@AuthenticationPrincipal Actor actor, @PathVariable UUID id,
            @Valid @RequestBody UpdateBranchRequest request) {
        return branches.update(actor, id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('branches.manage')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Actor actor, @PathVariable UUID id) {
        branches.delete(actor, id);
    }
}
