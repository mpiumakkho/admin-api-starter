package com.adminstarter.accesscontrol.liveupdate.controller;

import com.adminstarter.accesscontrol.liveupdate.service.ChangeStreamService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/events")
public class EventStreamController {

    /** Even a token with a long life is checked again at least this often */
    private static final Duration MAX_LIFETIME = Duration.ofMinutes(30);

    private final ChangeStreamService changes;
    private final Clock clock;

    public EventStreamController(ChangeStreamService changes, Clock clock) {
        this.changes = changes;
        this.clock = clock;
    }

    /**
     * Server-sent events, closed when the access token runs out: nobody keeps hearing about changes on an expired
     * token or an ended session. The client refreshes and connects again.
     */
    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("isAuthenticated()")
    public SseEmitter stream(Authentication authentication) {
        Instant expiresAt = authentication.getCredentials() instanceof Jwt jwt ? jwt.getExpiresAt() : null;
        var lifetime = expiresAt == null ? MAX_LIFETIME : Duration.between(clock.instant(), expiresAt);
        return changes.open(lifetime.compareTo(MAX_LIFETIME) > 0 ? MAX_LIFETIME : lifetime);
    }
}
