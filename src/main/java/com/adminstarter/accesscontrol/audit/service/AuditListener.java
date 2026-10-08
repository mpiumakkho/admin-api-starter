package com.adminstarter.accesscontrol.audit.service;

import com.adminstarter.accesscontrol.audit.entity.AuditLog;
import com.adminstarter.accesscontrol.audit.event.ActivityRecorded;
import com.adminstarter.accesscontrol.audit.repository.AuditLogRepository;
import com.adminstarter.accesscontrol.authentication.event.SessionsEnded;
import com.adminstarter.accesscontrol.authentication.event.SignedIn;
import com.adminstarter.accesscontrol.authorization.event.RoleCreated;
import com.adminstarter.accesscontrol.authorization.event.RoleDeleted;
import com.adminstarter.accesscontrol.authorization.event.RoleUpdated;
import com.adminstarter.accesscontrol.common.context.RequestContext;
import com.adminstarter.accesscontrol.common.dto.FieldChange;
import com.adminstarter.accesscontrol.identity.event.TwoFactorChanged;
import com.adminstarter.accesscontrol.identity.event.UserCreated;
import com.adminstarter.accesscontrol.identity.event.UserDeleted;
import com.adminstarter.accesscontrol.identity.event.UserInvitationResent;
import com.adminstarter.accesscontrol.identity.event.UserPasswordChanged;
import com.adminstarter.accesscontrol.identity.event.UserPasswordReset;
import com.adminstarter.accesscontrol.identity.event.UserStatusChanged;
import com.adminstarter.accesscontrol.identity.event.UserUpdated;
import com.adminstarter.accesscontrol.settings.event.SettingsUpdated;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes the activity log: one entry per change, with who did it, from where (RequestContext) and which fields
 * changed. It listens to the other modules' events and imports nothing else of theirs. Entries are written in the
 * transaction of the change, so a change is never kept without its entry, nor an entry without its change.
 */
@Service
public class AuditListener {

    private final AuditLogRepository logs;
    private final ApplicationEventPublisher events;
    private final JsonMapper json;
    private final Clock clock;

    public AuditListener(AuditLogRepository logs, ApplicationEventPublisher events, JsonMapper json, Clock clock) {
        this.logs = logs;
        this.events = events;
        this.json = json;
        this.clock = clock;
    }

    @EventListener
    void on(UserCreated event) {
        record(event.actorId(), event.invited() ? "user.invited" : "user.created", event.fullName(), event.userId(), event.changes());
    }

    @EventListener
    void on(UserInvitationResent event) {
        record(event.actorId(), "user.invited", event.fullName(), event.userId(), List.of());
    }

    @EventListener
    void on(UserUpdated event) {
        record(event.actorId(), "user.updated", event.fullName(), event.userId(), event.changes());
    }

    @EventListener
    void on(UserStatusChanged event) {
        var action = "suspended".equals(event.to()) ? "user.suspended" : "user.activated";
        record(event.actorId(), action, event.fullName(), event.userId(), List.of(FieldChange.of("status", event.from(), event.to())));
    }

    /** No target id: the user is gone, so there is nothing to link to */
    @EventListener
    void on(UserDeleted event) {
        record(event.actorId(), "user.deleted", event.fullName(), null, event.changes());
    }

    @EventListener
    void on(UserPasswordReset event) {
        record(event.actorId(), "user.password_reset", event.fullName(), event.userId(), List.of());
    }

    @EventListener
    void on(UserPasswordChanged event) {
        record(event.userId(), "user.password_changed", event.fullName(), event.userId(), List.of());
    }

    @EventListener
    void on(TwoFactorChanged event) {
        record(event.actorId(), event.enabled() ? "auth.two_factor_on" : "auth.two_factor_off", event.fullName(),
                event.userId(), List.of());
    }

    @EventListener
    void on(SignedIn event) {
        record(event.userId(), "auth.sign_in", event.fullName(), event.userId(), List.of());
    }

    @EventListener
    void on(SessionsEnded event) {
        record(event.actorId(), "auth.sessions_ended", event.fullName(), event.userId(),
                List.of(FieldChange.list("devices", List.of(), event.devices())));
    }

    @EventListener
    void on(RoleCreated event) {
        record(event.actorId(), "role.created", event.name(), event.roleId(), event.changes());
    }

    @EventListener
    void on(RoleUpdated event) {
        record(event.actorId(), "role.updated", event.name(), event.roleId(), event.changes());
    }

    @EventListener
    void on(RoleDeleted event) {
        record(event.actorId(), "role.deleted", event.name(), null, event.changes());
    }

    @EventListener
    void on(SettingsUpdated event) {
        record(event.actorId(), "settings.updated", "Settings", null, event.changes());
    }

    private void record(UUID actorId, String action, String targetLabel, @Nullable UUID targetId, List<FieldChange> changes) {
        var context = RequestContext.currentOrNone();
        var entry = logs.save(new AuditLog(clock.instant().truncatedTo(ChronoUnit.MILLIS), actorId, action, targetLabel,
                targetId == null ? null : targetId.toString(), changes.isEmpty() ? null : json.writeValueAsString(changes),
                context.ip(), context.browser(), context.os(), context.requestId()));
        events.publishEvent(new ActivityRecorded(entry.getId(), action));
    }
}
