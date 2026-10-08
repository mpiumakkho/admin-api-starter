package com.adminstarter.accesscontrol.settings.service;

import static com.adminstarter.accesscontrol.common.constant.ErrorCode.NOT_FOUND;

import com.adminstarter.accesscontrol.authorization.service.RoleService;
import com.adminstarter.accesscontrol.common.context.Actor;
import com.adminstarter.accesscontrol.common.dto.FieldChange;
import com.adminstarter.accesscontrol.settings.dto.SettingsResponse;
import com.adminstarter.accesscontrol.settings.dto.UpdateSettingsRequest;
import com.adminstarter.accesscontrol.settings.entity.AdminSettings;
import com.adminstarter.accesscontrol.settings.event.SettingsUpdated;
import com.adminstarter.accesscontrol.settings.repository.AdminSettingsRepository;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class SettingsServiceImpl implements SettingsService {

    private final AdminSettingsRepository settings;
    private final RoleService roles;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public SettingsServiceImpl(AdminSettingsRepository settings, RoleService roles, ApplicationEventPublisher events,
            Clock clock) {
        this.settings = settings;
        this.roles = roles;
        this.events = events;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public SettingsResponse current() {
        return toResponse(load());
    }

    public SettingsResponse update(Actor actor, UpdateSettingsRequest request) {
        var current = load();
        if (request.defaultRoleId() != null && roles.find(request.defaultRoleId()).isEmpty()) {
            throw NOT_FOUND.exception();
        }
        var defaultRoleId = Objects.requireNonNullElse(request.defaultRoleId(), current.getDefaultRoleId());
        var domains = request.allowedDomains() == null ? current.getAllowedDomains() : normalize(request.allowedDomains());
        var inviteExpiryDays = Objects.requireNonNullElse(request.inviteExpiryDays(), current.getInviteExpiryDays());
        var sessionTimeoutMinutes =
                Objects.requireNonNullElse(request.sessionTimeoutMinutes(), current.getSessionTimeoutMinutes());

        var changes = new ArrayList<FieldChange>();
        addIfChanged(changes, "defaultRoleId", current.getDefaultRoleId(), defaultRoleId);
        addIfChanged(changes, "inviteExpiryDays", current.getInviteExpiryDays(), inviteExpiryDays);
        addIfChanged(changes, "sessionTimeoutMinutes", current.getSessionTimeoutMinutes(), sessionTimeoutMinutes);
        var added = domains.stream().filter(domain -> !current.getAllowedDomains().contains(domain)).toList();
        var removed = current.getAllowedDomains().stream().filter(domain -> !domains.contains(domain)).toList();
        if (!added.isEmpty() || !removed.isEmpty()) {
            changes.add(FieldChange.list("allowedDomains", added, removed));
        }

        current.update(defaultRoleId, domains, inviteExpiryDays, sessionTimeoutMinutes,
                clock.instant().truncatedTo(ChronoUnit.MILLIS));
        if (!changes.isEmpty()) {
            events.publishEvent(new SettingsUpdated(actor.userId(), List.copyOf(changes)));
        }
        return toResponse(current);
    }

    private AdminSettings load() {
        return settings.findById(AdminSettings.ID)
                .orElseThrow(() -> new IllegalStateException("The settings row is missing; SettingsInitializer creates it"));
    }

    /** Lower-cased, trimmed, without blanks or duplicates, in the order given */
    private static List<String> normalize(List<String> domains) {
        return domains.stream()
                .map(domain -> domain.trim().toLowerCase(Locale.ROOT))
                .filter(domain -> !domain.isEmpty())
                .distinct()
                .toList();
    }

    private static void addIfChanged(List<FieldChange> changes, String field, Object from, Object to) {
        if (!from.equals(to)) {
            changes.add(FieldChange.of(field, String.valueOf(from), String.valueOf(to)));
        }
    }

    private static SettingsResponse toResponse(AdminSettings settings) {
        return new SettingsResponse(settings.getDefaultRoleId(), settings.getAllowedDomains(), settings.getInviteExpiryDays(),
                settings.getSessionTimeoutMinutes());
    }
}
