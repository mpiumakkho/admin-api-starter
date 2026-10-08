package com.adminstarter.accesscontrol.identity.controller;

import com.adminstarter.accesscontrol.common.context.Actor;
import com.adminstarter.accesscontrol.identity.dto.CreateUserRequest;
import com.adminstarter.accesscontrol.identity.dto.DeleteUsersRequest;
import com.adminstarter.accesscontrol.identity.dto.ImportUsersRequest;
import com.adminstarter.accesscontrol.identity.dto.SetUserStatusRequest;
import com.adminstarter.accesscontrol.identity.dto.UpdateUserRequest;
import com.adminstarter.accesscontrol.identity.dto.UserResponse;
import com.adminstarter.accesscontrol.identity.service.TwoFactorService;
import com.adminstarter.accesscontrol.identity.service.UserLifecycleService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserLifecycleService users;
    private final TwoFactorService twoFactor;

    public UserController(UserLifecycleService users, TwoFactorService twoFactor) {
        this.users = users;
        this.twoFactor = twoFactor;
    }

    /** Everyone with users.view; without it, only yourself */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<UserResponse> list(@AuthenticationPrincipal Actor actor) {
        return users.list(actor);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('users.create')")
    public UserResponse create(@AuthenticationPrincipal Actor actor, @Valid @RequestBody CreateUserRequest request) {
        return users.create(actor, request);
    }

    @PostMapping("/import")
    @PreAuthorize("hasAuthority('users.create')")
    public List<UserResponse> importUsers(@AuthenticationPrincipal Actor actor, @Valid @RequestBody ImportUsersRequest request) {
        return users.importUsers(actor, request);
    }

    /** users.edit; without it, people may change their own name, phone and photo */
    @PatchMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public UserResponse update(@AuthenticationPrincipal Actor actor, @PathVariable UUID id,
            @Valid @RequestBody UpdateUserRequest request) {
        return users.update(actor, id, request);
    }

    @PostMapping("/status")
    @PreAuthorize("hasAuthority('users.edit')")
    public List<UserResponse> setStatus(@AuthenticationPrincipal Actor actor, @Valid @RequestBody SetUserStatusRequest request) {
        return users.setStatus(actor, request);
    }

    @PostMapping("/delete")
    @PreAuthorize("hasAuthority('users.delete')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Actor actor, @Valid @RequestBody DeleteUsersRequest request) {
        users.delete(actor, request);
    }

    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasAuthority('users.edit')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@AuthenticationPrincipal Actor actor, @PathVariable UUID id) {
        users.resetPassword(actor, id);
    }

    @PostMapping("/{id}/resend-invite")
    @PreAuthorize("hasAuthority('users.create')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resendInvite(@AuthenticationPrincipal Actor actor, @PathVariable UUID id) {
        users.resendInvite(actor, id);
    }

    @PostMapping("/{id}/two-factor/reset")
    @PreAuthorize("hasAuthority('users.edit')")
    public UserResponse resetTwoFactor(@AuthenticationPrincipal Actor actor, @PathVariable UUID id) {
        return twoFactor.reset(actor, id);
    }
}
