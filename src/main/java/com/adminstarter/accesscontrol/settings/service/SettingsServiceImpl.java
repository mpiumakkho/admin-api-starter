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
import java.util.UUID;
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
        if (request.defaultRoleId() != null) {
            requireRole(request.defaultRoleId());
        }
        if (request.twoFactorRoleIds() != null) {
            request.twoFactorRoleIds().forEach(this::requireRole);
        }
        var defaultRoleId = Objects.requireNonNullElse(request.defaultRoleId(), current.getDefaultRoleId());
        var domains = request.allowedDomains() == null ? current.getAllowedDomains() : normalize(request.allowedDomains());
        var inviteExpiryDays = Objects.requireNonNullElse(request.inviteExpiryDays(), current.getInviteExpiryDays());
        var sessionTimeoutMinutes =
                Objects.requireNonNullElse(request.sessionTimeoutMinutes(), current.getSessionTimeoutMinutes());
        var twoFactorRoleIds = request.twoFactorRoleIds() == null ? current.getTwoFactorRoleIds()
                : request.twoFactorRoleIds().stream().distinct().toList();
        var passwordMinLength = Objects.requireNonNullElse(request.passwordMinLength(), current.getPasswordMinLength());
        var passwordHistory = Objects.requireNonNullElse(request.passwordHistory(), current.getPasswordHistory());
        var activityRetentionDays =
                Objects.requireNonNullElse(request.activityRetentionDays(), current.getActivityRetentionDays());

        // In the order of AdminSettings in models.ts, the two lists last, as items added and removed
        var changes = new ArrayList<FieldChange>();
        addIfChanged(changes, "defaultRoleId", current.getDefaultRoleId(), defaultRoleId);
        addIfChanged(changes, "inviteExpiryDays", current.getInviteExpiryDays(), inviteExpiryDays);
        addIfChanged(changes, "sessionTimeoutMinutes", current.getSessionTimeoutMinutes(), sessionTimeoutMinutes);
        addIfChanged(changes, "passwordMinLength", current.getPasswordMinLength(), passwordMinLength);
        addIfChanged(changes, "passwordHistory", current.getPasswordHistory(), passwordHistory);
        addIfChanged(changes, "activityRetentionDays", current.getActivityRetentionDays(), activityRetentionDays);
        addIfListChanged(changes, "allowedDomains", current.getAllowedDomains(), domains);
        addIfListChanged(changes, "twoFactorRoleIds", strings(current.getTwoFactorRoleIds()), strings(twoFactorRoleIds));

        var now = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        current.update(defaultRoleId, domains, inviteExpiryDays, sessionTimeoutMinutes, now);
        current.updateAccountRules(twoFactorRoleIds, passwordMinLength, passwordHistory, activityRetentionDays, now);
        if (!changes.isEmpty()) {
            events.publishEvent(new SettingsUpdated(actor.userId(), List.copyOf(changes)));
        }
        return toResponse(current);
    }

    private AdminSettings load() {
        return settings.findById(AdminSettings.ID)
                .orElseThrow(() -> new IllegalStateException("The settings row is missing; SettingsInitializer creates it"));
    }

    private void requireRole(UUID roleId) {
        if (roles.find(roleId).isEmpty()) {
            throw NOT_FOUND.exception();
        }
    }

    /** Lower-cased, trimmed, without blanks or duplicates, in the order given */
    private static List<String> normalize(List<String> domains) {
        return domains.stream()
                .map(domain -> domain.trim().toLowerCase(Locale.ROOT))
                .filter(domain -> !domain.isEmpty())
                .distinct()
                .toList();
    }

    private static List<String> strings(List<UUID> ids) {
        return ids.stream().map(UUID::toString).toList();
    }

    private static void addIfChanged(List<FieldChange> changes, String field, Object from, Object to) {
        if (!from.equals(to)) {
            changes.add(FieldChange.of(field, String.valueOf(from), String.valueOf(to)));
        }
    }

    private static void addIfListChanged(List<FieldChange> changes, String field, List<String> from, List<String> to) {
        var added = to.stream().filter(item -> !from.contains(item)).toList();
        var removed = from.stream().filter(item -> !to.contains(item)).toList();
        if (!added.isEmpty() || !removed.isEmpty()) {
            changes.add(FieldChange.list(field, added, removed));
        }
    }

    private static SettingsResponse toResponse(AdminSettings settings) {
        return new SettingsResponse(settings.getDefaultRoleId(), settings.getAllowedDomains(), settings.getInviteExpiryDays(),
                settings.getSessionTimeoutMinutes(), settings.getTwoFactorRoleIds(), settings.getPasswordMinLength(),
                settings.getPasswordHistory(), settings.getActivityRetentionDays());
    }
}
