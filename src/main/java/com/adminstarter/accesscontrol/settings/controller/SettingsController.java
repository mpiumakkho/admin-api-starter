package com.adminstarter.accesscontrol.settings.controller;

import com.adminstarter.accesscontrol.common.context.Actor;
import com.adminstarter.accesscontrol.settings.dto.SettingsResponse;
import com.adminstarter.accesscontrol.settings.dto.UpdateSettingsRequest;
import com.adminstarter.accesscontrol.settings.service.SettingsServiceImpl;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private final SettingsServiceImpl settings;

    public SettingsController(SettingsServiceImpl settings) {
        this.settings = settings;
    }

    /** Everyone signed in reads them, since sign-in and the user form follow them */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public SettingsResponse get() {
        return settings.current();
    }

    @PatchMapping
    @PreAuthorize("hasAuthority('settings.manage')")
    public SettingsResponse update(@AuthenticationPrincipal Actor actor, @Valid @RequestBody UpdateSettingsRequest request) {
        return settings.update(actor, request);
    }
}
