package com.adminstarter.accesscontrol.identity.service;

import com.adminstarter.accesscontrol.identity.event.RoleAssignmentExpired;
import com.adminstarter.accesscontrol.identity.repository.RoleAssignmentRepository;
import com.adminstarter.accesscontrol.identity.repository.UserRepository;
import com.adminstarter.accesscontrol.settings.service.SettingsService;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every five minutes, logs each role assignment that has reached its end, once, as role_assignment.expired with no
 * actor, and deletes ended ones older than the activity the settings keep (P-019). The role stopped counting at its
 * end already: permissions are worked out on every request. Several instances may run it at once; marking an end as
 * logged only succeeds once, so each is logged once.
 */
@Service
public class RoleAssignmentExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(RoleAssignmentExpiryJob.class);

    private final RoleAssignmentRepository assignments;
    private final UserRepository users;
    private final SettingsService settings;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public RoleAssignmentExpiryJob(RoleAssignmentRepository assignments, UserRepository users, SettingsService settings,
            ApplicationEventPublisher events, Clock clock) {
        this.assignments = assignments;
        this.users = users;
        this.settings = settings;
        this.events = events;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "PT5M", initialDelayString = "PT1M")
    @Transactional
    public void run() {
        var now = clock.instant();
        var logged = 0;
        for (var assignment : assignments.findEndedUnlogged(now)) {
            if (assignments.markExpiryLogged(assignment.getId(), now) == 0) {
                continue;
            }
            var user = users.findById(assignment.getUserId());
            if (user.isPresent()) {
                events.publishEvent(new RoleAssignmentExpired(user.get().getId(), user.get().getFullName(),
                        assignment.getRoleId(), Objects.requireNonNull(assignment.getEndsAt())));
                logged++;
            }
        }
        var kept = Duration.ofDays(settings.current().activityRetentionDays());
        var deleted = assignments.deleteEndedBefore(now.minus(kept));
        if (logged > 0 || deleted > 0) {
            log.info("Role assignments: {} ended, {} old ones deleted", logged, deleted);
        }
    }
}
