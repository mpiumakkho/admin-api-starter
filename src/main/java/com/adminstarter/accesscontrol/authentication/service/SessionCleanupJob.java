package com.adminstarter.accesscontrol.authentication.service;

import com.adminstarter.accesscontrol.AccessControlProperties;
import com.adminstarter.accesscontrol.authentication.repository.ActionTokenRepository;
import com.adminstarter.accesscontrol.authentication.repository.UserSessionRepository;
import java.time.Clock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every night, deletes sessions that ended or ran out more than a month ago (with their refresh tokens) and email
 * links that expired as long ago. Until then a replaced refresh token can still be recognised if it is used again.
 */
@Service
public class SessionCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(SessionCleanupJob.class);
    private static final Duration KEEP = Duration.ofDays(30);

    private final UserSessionRepository sessions;
    private final ActionTokenRepository actionTokens;
    private final Clock clock;

    public SessionCleanupJob(UserSessionRepository sessions, ActionTokenRepository actionTokens, Clock clock) {
        this.sessions = sessions;
        this.actionTokens = actionTokens;
        this.clock = clock;
    }

    @Scheduled(cron = "0 15 3 * * *", zone = AccessControlProperties.TIME_ZONE)
    @Transactional
    public void run() {
        var before = clock.instant().minus(KEEP);
        var deletedSessions = sessions.deleteClosedBefore(before);
        var deletedLinks = actionTokens.deleteExpiredBefore(before);
        log.info("Deleted {} old sessions and {} expired email links", deletedSessions, deletedLinks);
    }
}
