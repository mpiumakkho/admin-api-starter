package com.adminstarter.accesscontrol.settings.service;

import static com.adminstarter.accesscontrol.common.constant.ErrorCode.NOT_FOUND;

import com.adminstarter.accesscontrol.authorization.service.RoleService;
import com.adminstarter.accesscontrol.common.context.Actor;
import com.adminstarter.accesscontrol.common.dto.FieldChange;
import com.adminstarter.accesscontrol.settings.dto.PasswordPolicyResponse;
import com.adminstarter.accesscontrol.settings.dto.SettingsResponse;
import com.adminstarter.accesscontrol.settings.dto.UpdateSettingsRequest;
import com.adminstarter.accesscontrol.settings.entity.AdminSettings;
import com.adminstarter.accesscontrol.settings.event.SettingsUpdated;
import com.adminstarter.accesscontrol.settings.repository.AdminSettingsRepository;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class SettingsServiceImpl implements SettingsService {

    /** requiresTwoFactor; evicted, after commit, whenever twoFactorRoleIds may have changed */
    static final String TWO_FACTOR_ROLES_CACHE = "two-factor-roles";

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

    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = TWO_FACTOR_ROLES_CACHE)
    public boolean requiresTwoFactor(UUID roleId) {
        return load().getTwoFactorRoleIds().contains(roleId);
    }

    @Override
    @Transactional(readOnly = true)
    public PasswordPolicyResponse passwordPolicy() {
        var current = load();
        return new PasswordPolicyResponse(current.getPasswordMinLength(), current.getPasswordHistory());
    }

    @CacheEvict(cacheNames = TWO_FACTOR_ROLES_CACHE, allEntries = true)
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
        var branchesEnabled = Objects.requireNonNullElse(request.branchesEnabled(), current.isBranchesEnabled());

        // In the order of AdminSettings in models.ts, the two lists last, as items added and removed
        var changes = new ArrayList<FieldChange>();
        FieldChange.diff("defaultRoleId", current.getDefaultRoleId(), defaultRoleId).ifPresent(changes::add);
        FieldChange.diff("inviteExpiryDays", current.getInviteExpiryDays(), inviteExpiryDays).ifPresent(changes::add);
        FieldChange.diff("sessionTimeoutMinutes", current.getSessionTimeoutMinutes(), sessionTimeoutMinutes)
                .ifPresent(changes::add);
        FieldChange.diff("passwordMinLength", current.getPasswordMinLength(), passwordMinLength).ifPresent(changes::add);
        FieldChange.diff("passwordHistory", current.getPasswordHistory(), passwordHistory).ifPresent(changes::add);
        FieldChange.diff("activityRetentionDays", current.getActivityRetentionDays(), activityRetentionDays)
                .ifPresent(changes::add);
        FieldChange.diff("branchesEnabled", current.isBranchesEnabled(), branchesEnabled).ifPresent(changes::add);
        FieldChange.listDiff("allowedDomains", current.getAllowedDomains(), domains).ifPresent(changes::add);
        FieldChange.listDiff("twoFactorRoleIds", strings(current.getTwoFactorRoleIds()), strings(twoFactorRoleIds))
                .ifPresent(changes::add);

        var now = clock.instant();
        current.update(defaultRoleId, domains, inviteExpiryDays, sessionTimeoutMinutes, now);
        current.updateAccountRules(twoFactorRoleIds, passwordMinLength, passwordHistory, activityRetentionDays, now);
        current.changeBranchesEnabled(branchesEnabled, now);
        if (!changes.isEmpty()) {
            events.publishEvent(new SettingsUpdated(actor.userId(), List.copyOf(changes)));
        }
        return toResponse(current);
    }

    /** A deleted role requires nothing any more: it comes off twoFactorRoleIds */
    @CacheEvict(cacheNames = TWO_FACTOR_ROLES_CACHE, allEntries = true)
    public void forgetRole(UUID roleId) {
        load().removeTwoFactorRole(roleId, clock.instant());
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


    private static SettingsResponse toResponse(AdminSettings settings) {
        return new SettingsResponse(settings.getDefaultRoleId(), settings.getAllowedDomains(), settings.getInviteExpiryDays(),
                settings.getSessionTimeoutMinutes(), settings.getTwoFactorRoleIds(), settings.getPasswordMinLength(),
                settings.getPasswordHistory(), settings.getActivityRetentionDays(), settings.isBranchesEnabled());
    }
}
