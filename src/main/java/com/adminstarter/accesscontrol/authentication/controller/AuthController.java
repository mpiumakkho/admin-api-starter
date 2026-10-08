package com.adminstarter.accesscontrol.authentication.controller;

import com.adminstarter.accesscontrol.authentication.dto.EmailLinkRequest;
import com.adminstarter.accesscontrol.authentication.dto.LoginRequest;
import com.adminstarter.accesscontrol.authentication.dto.PasswordResetRequest;
import com.adminstarter.accesscontrol.authentication.dto.TokenResponse;
import com.adminstarter.accesscontrol.authentication.service.ActionTokenService;
import com.adminstarter.accesscontrol.authentication.service.LoginService;
import com.adminstarter.accesscontrol.authentication.service.RefreshCookieSupport;
import com.adminstarter.accesscontrol.common.error.ApiExceptionHandler;
import com.adminstarter.accesscontrol.common.exception.AccessControlException;
import com.adminstarter.accesscontrol.settings.dto.PasswordPolicyResponse;
import com.adminstarter.accesscontrol.settings.service.SettingsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Open to everyone (SecurityConfig), limited per IP address (RateLimitFilter). Works from the request body or the
 * refresh cookie, never from an access token.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final LoginService login;
    private final ActionTokenService actionTokens;
    private final RefreshCookieSupport cookies;
    private final SettingsService settings;

    public AuthController(LoginService login, ActionTokenService actionTokens, RefreshCookieSupport cookies,
            SettingsService settings) {
        this.login = login;
        this.actionTokens = actionTokens;
        this.cookies = cookies;
        this.settings = settings;
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        cookies.checkOrigin(http);
        return answer(login.login(request));
    }

    /** A refused refresh also clears the cookie, so the browser stops sending one that will never work */
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(HttpServletRequest http) {
        cookies.checkOrigin(http);
        try {
            return answer(login.refresh(cookies.read(http).orElse(null)));
        } catch (AccessControlException refused) {
            return ResponseEntity.status(refused.code().status())
                    .header(HttpHeaders.SET_COOKIE, cookies.clear().toString())
                    .body(ApiExceptionHandler.problem(refused.code()));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest http) {
        cookies.checkOrigin(http);
        login.logout(cookies.read(http).orElse(null));
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookies.clear().toString()).build();
    }

    @PostMapping("/activate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void activate(@Valid @RequestBody EmailLinkRequest request) {
        actionTokens.acceptInvitation(request.token(), request.password());
    }

    /** 202 whether or not the email belongs to someone */
    @PostMapping("/password-reset/request")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void requestPasswordReset(@Valid @RequestBody PasswordResetRequest request) {
        actionTokens.requestPasswordReset(request.email());
    }

    @PostMapping("/password-reset/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirmPasswordReset(@Valid @RequestBody EmailLinkRequest request) {
        actionTokens.confirmPasswordReset(request.token(), request.password());
    }

    /** The password rules, so the pages for the links in emails can show them before anyone signs in */
    @GetMapping("/password-policy")
    public PasswordPolicyResponse passwordPolicy() {
        return settings.passwordPolicy();
    }

    private ResponseEntity<TokenResponse> answer(LoginService.SignIn signIn) {
        var response = ResponseEntity.ok();
        if (signIn.refreshToken() != null) {
            response.header(HttpHeaders.SET_COOKIE, cookies.issue(signIn.refreshToken(), signIn.cookieMaxAge()).toString());
        }
        return response.body(signIn.body());
    }
}
