package com.adminstarter.accesscontrol.authentication.controller;

import com.adminstarter.accesscontrol.authentication.dto.UserSessionResponse;
import com.adminstarter.accesscontrol.authentication.service.SessionService;
import com.adminstarter.accesscontrol.common.context.Actor;
import com.adminstarter.accesscontrol.identity.dto.ReasonRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Signed-in devices: your own, or, with users.edit, those of people you manage (checked in SessionService) */
@RestController
@RequestMapping("/api")
public class SessionController {

    private final SessionService sessions;

    public SessionController(SessionService sessions) {
        this.sessions = sessions;
    }

    @GetMapping("/users/{id}/sessions")
    @PreAuthorize("isAuthenticated()")
    public List<UserSessionResponse> list(@AuthenticationPrincipal Actor actor, @PathVariable UUID id) {
        return sessions.list(actor, id);
    }

    @DeleteMapping("/sessions/{id}")
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void end(@AuthenticationPrincipal Actor actor, @PathVariable UUID id,
            @Valid @RequestBody(required = false) @Nullable ReasonRequest request) {
        sessions.end(actor, id, ReasonRequest.of(request));
    }

    /** Everywhere; for yourself, everywhere but this browser */
    @PostMapping("/users/{id}/sessions/end")
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void endAll(@AuthenticationPrincipal Actor actor, @PathVariable UUID id,
            @Valid @RequestBody(required = false) @Nullable ReasonRequest request) {
        sessions.endAll(actor, id, ReasonRequest.of(request));
    }
}
