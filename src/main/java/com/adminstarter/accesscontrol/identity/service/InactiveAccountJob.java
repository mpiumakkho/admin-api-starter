package com.adminstarter.accesscontrol.identity.service;

import com.adminstarter.accesscontrol.identity.entity.UserStatus;
import com.adminstarter.accesscontrol.identity.event.UserInactiveSuspended;
import com.adminstarter.accesscontrol.identity.event.UserSuspended;
import com.adminstarter.accesscontrol.identity.repository.UserRepository;
import com.adminstarter.accesscontrol.settings.service.SettingsService;
import java.time.Clock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every night, suspends the active accounts nobody used for the settings' suspendInactiveDays (P-027), as IT audits
 * ask of accounts left open: their sessions end, the activity log says why, and they get an email. Never the last
 * active user who can run the system (rule 6). Off while the setting is 0.
 */
@Service
public class InactiveAccountJob {

    private static final Logger log = LoggerFactory.getLogger(InactiveAccountJob.class);

    private final UserRepository users;
    private final RoleAssignmentResolver resolver;
    private final SettingsService settings;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public InactiveAccountJob(UserRepository users, RoleAssignmentResolver resolver, SettingsService settings,
            ApplicationEventPublisher events, Clock clock) {
        this.users = users;
        this.resolver = resolver;
        this.settings = settings;
        this.events = events;
        this.clock = clock;
    }

    @Scheduled(cron = "0 30 2 * * *")
    @Transactional
    @CacheEvict(cacheNames = UserServiceImpl.ACCOUNTS_CACHE, allEntries = true)
    public void run() {
        var days = settings.current().suspendInactiveDays();
        if (days == 0) {
            return;
        }
        var now = clock.instant();
        var suspended = 0;
        for (var user : users.findInactiveSince(now.minus(Duration.ofDays(days)))) {
            // The last one who can run the system stays, whenever they last signed in
            if (resolver.isAdministrator(user.getId()) && resolver.administrators() <= 1) {
                continue;
            }
            user.changeStatus(UserStatus.SUSPENDED, now);
            users.flush();
            events.publishEvent(new UserInactiveSuspended(user.getId(), user.getFullName(), user.getEmail(), user.getFirstName(), days));
            events.publishEvent(new UserSuspended(user.getId()));
            suspended++;
        }
        if (suspended > 0) {
            log.info("Suspended {} accounts not used for {} days", suspended, days);
        }
    }
}
