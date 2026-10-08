package com.adminstarter.accesscontrol.audit.controller;

import com.adminstarter.accesscontrol.audit.dto.ActivityResponse;
import com.adminstarter.accesscontrol.audit.service.ActivityQueryService;
import com.adminstarter.accesscontrol.common.context.Actor;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/activity")
public class ActivityController {

    private final ActivityQueryService activity;

    public ActivityController(ActivityQueryService activity) {
        this.activity = activity;
    }

    /**
     * activity.view; without it, an empty list rather than a refusal. {@code from} and {@code to} are optional ISO 8601
     * instants, such as 2026-01-31T17:00:00Z: entries at or after {@code from} and before {@code to}.
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<ActivityResponse> list(@AuthenticationPrincipal Actor actor,
            @RequestParam(required = false) @Nullable Instant from, @RequestParam(required = false) @Nullable Instant to) {
        return activity.list(actor, from, to);
    }
}
