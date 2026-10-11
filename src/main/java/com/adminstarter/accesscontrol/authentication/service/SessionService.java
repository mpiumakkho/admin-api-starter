package com.adminstarter.accesscontrol.authentication.service;

import static com.adminstarter.accesscontrol.common.constant.ErrorCode.NOT_FOUND;

import com.adminstarter.accesscontrol.authentication.dto.UserSessionResponse;
import com.adminstarter.accesscontrol.authentication.entity.UserSession;
import com.adminstarter.accesscontrol.authentication.event.SessionsEnded;
import com.adminstarter.accesscontrol.authentication.repository.UserSessionRepository;
import com.adminstarter.accesscontrol.common.context.Actor;
import com.adminstarter.accesscontrol.identity.dto.UserResponse;
import com.adminstarter.accesscontrol.identity.service.UserService;
import com.adminstarter.accesscontrol.security.SessionChecker;
import com.adminstarter.accesscontrol.settings.service.SettingsService;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Signed-in devices: the check on every request, the devices list, and ending sessions. Your own, or, with
 * users.edit, those of someone you manage.
 */
@Service
@Transactional
public class SessionService implements SessionChecker {

    /** How often "last seen" is brought up to date, so every request is not a write */
    private static final Duration SEEN_EVERY = Duration.ofMinutes(1);

    private final UserSessionRepository sessions;
    private final UserService users;
    private final SettingsService settings;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public SessionService(UserSessionRepository sessions, UserService users, SettingsService settings,
            ApplicationEventPublisher events, Clock clock) {
        this.sessions = sessions;
        this.users = users;
        this.settings = settings;
        this.events = events;
        this.clock = clock;
    }

    @Override
    public boolean isOpen(UUID sessionId, UUID userId) {
        var now = clock.instant();
        var session = sessions.findById(sessionId).orElse(null);
        if (session == null || !session.getUserId().equals(userId) || !session.openAt(now)) {
            return false;
        }
        if (session.getLastSeenAt().plus(SEEN_EVERY).isBefore(now)) {
            session.seen(now);
        }
        return true;
    }

    @Transactional(readOnly = true)
    public List<UserSessionResponse> list(Actor actor, UUID userId) {
        users.requireSecurityManageable(actor, userId);
        return sessions.findOpen(userId, clock.instant()).stream()
                .map(session -> new UserSessionResponse(session.getId(), session.getUserId(), session.getCreatedAt(),
                        session.getLastSeenAt(), session.getIp(), session.getBrowser(), session.getOs(),
                        session.getId().equals(actor.sessionId())))
                .toList();
    }

    /** Someone else's needs a reason when the settings say so (P-021); your own never does */
    public void end(Actor actor, UUID sessionId, @Nullable String reason) {
        var now = clock.instant();
        var session = sessions.findById(sessionId).filter(candidate -> candidate.openAt(now)).orElseThrow(NOT_FOUND::exception);
        var user = users.requireSecurityManageable(actor, session.getUserId());
        var why = actor.is(user.id()) ? "" : settings.requireReason("endSessions", reason);
        session.end(now);
        events.publishEvent(new SessionsEnded(actor.userId(), user.id(), fullName(user), List.of(session.getDeviceLabel()), why));
    }

    /** Everywhere; for yourself, everywhere but the browser asking */
    public void endAll(Actor actor, UUID userId, @Nullable String reason) {
        var user = users.requireSecurityManageable(actor, userId);
        var why = actor.is(userId) ? "" : settings.requireReason("endSessions", reason);
        var ended = endOpen(userId, actor.sessionId());
        if (!ended.isEmpty()) {
            events.publishEvent(new SessionsEnded(actor.userId(), userId, fullName(user), ended, why));
        }
    }

    /** Ends a user's open sessions but {@code keep}, answering their device labels */
    List<String> endOpen(UUID userId, @Nullable UUID keep) {
        var now = clock.instant();
        var ended = sessions.findOpen(userId, now).stream().filter(session -> !session.getId().equals(keep)).toList();
        ended.forEach(session -> session.end(now));
        return ended.stream().map(UserSession::getDeviceLabel).toList();
    }

    private static String fullName(UserResponse user) {
        return (user.firstName() + " " + user.lastName()).trim();
    }
}
