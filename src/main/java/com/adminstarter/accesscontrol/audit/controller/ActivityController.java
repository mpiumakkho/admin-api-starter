package com.adminstarter.accesscontrol.audit.controller;

import com.adminstarter.accesscontrol.audit.dto.ActivityResponse;
import com.adminstarter.accesscontrol.audit.service.ActivityQueryService;
import com.adminstarter.accesscontrol.common.context.Actor;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/activity")
public class ActivityController {

    private final ActivityQueryService activity;

    public ActivityController(ActivityQueryService activity) {
        this.activity = activity;
    }

    /** activity.view; without it, an empty list rather than a refusal */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<ActivityResponse> list(@AuthenticationPrincipal Actor actor) {
        return activity.list(actor);
    }
}
