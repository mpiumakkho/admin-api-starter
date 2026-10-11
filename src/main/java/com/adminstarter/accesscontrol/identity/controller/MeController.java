package com.adminstarter.accesscontrol.identity.controller;

import com.adminstarter.accesscontrol.common.context.Actor;
import com.adminstarter.accesscontrol.identity.dto.ChangePasswordRequest;
import com.adminstarter.accesscontrol.identity.dto.RecoveryCodesLeftResponse;
import com.adminstarter.accesscontrol.identity.dto.RecoveryCodesResponse;
import com.adminstarter.accesscontrol.identity.dto.TwoFactorCodeRequest;
import com.adminstarter.accesscontrol.identity.dto.TwoFactorConfirmationResponse;
import com.adminstarter.accesscontrol.identity.dto.TwoFactorSetupResponse;
import com.adminstarter.accesscontrol.identity.dto.UserResponse;
import com.adminstarter.accesscontrol.identity.service.CredentialServiceImpl;
import com.adminstarter.accesscontrol.identity.service.TwoFactorService;
import com.adminstarter.accesscontrol.identity.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in user's own account: no permission needed */
@RestController
@RequestMapping("/api/me")
public class MeController {

    private final UserService users;
    private final CredentialServiceImpl credentials;
    private final TwoFactorService twoFactor;

    public MeController(UserService users, CredentialServiceImpl credentials, TwoFactorService twoFactor) {
        this.users = users;
        this.credentials = credentials;
        this.twoFactor = twoFactor;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public UserResponse get(@AuthenticationPrincipal Actor actor) {
        return users.get(actor.userId());
    }

    @PostMapping("/password")
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@AuthenticationPrincipal Actor actor, @Valid @RequestBody ChangePasswordRequest request) {
        credentials.changeOwnPassword(actor, request.currentPassword(), request.newPassword());
    }

    @PostMapping("/two-factor")
    @PreAuthorize("isAuthenticated()")
    public TwoFactorSetupResponse startTwoFactor(@AuthenticationPrincipal Actor actor) {
        return twoFactor.start(actor);
    }

    @PostMapping("/two-factor/confirm")
    @PreAuthorize("isAuthenticated()")
    public TwoFactorConfirmationResponse confirmTwoFactor(@AuthenticationPrincipal Actor actor,
            @Valid @RequestBody TwoFactorCodeRequest request) {
        return twoFactor.confirm(actor, request.code());
    }

    @GetMapping("/recovery-codes")
    @PreAuthorize("isAuthenticated()")
    public RecoveryCodesLeftResponse recoveryCodesLeft(@AuthenticationPrincipal Actor actor) {
        return twoFactor.recoveryCodesLeft(actor);
    }

    @PostMapping("/recovery-codes")
    @PreAuthorize("isAuthenticated()")
    public RecoveryCodesResponse newRecoveryCodes(@AuthenticationPrincipal Actor actor) {
        return twoFactor.newRecoveryCodes(actor);
    }

    @PostMapping("/two-factor/disable")
    @PreAuthorize("isAuthenticated()")
    public UserResponse disableTwoFactor(@AuthenticationPrincipal Actor actor, @Valid @RequestBody TwoFactorCodeRequest request) {
        return twoFactor.disable(actor, request.code());
    }
}
