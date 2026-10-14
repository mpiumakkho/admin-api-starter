package com.adminstarter.accesscontrol.audit.service;

import com.adminstarter.accesscontrol.AccessControlProperties;
import com.adminstarter.accesscontrol.audit.repository.AuditLogRepository;
import com.adminstarter.accesscontrol.audit.spi.ActivityRetentionProvider;
import java.time.Clock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Every night, deletes activity older than the settings' activityRetentionDays (two years by default; P-018) */
@Service
public class AuditRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(AuditRetentionJob.class);

    private final AuditLogRepository logs;
    private final ActivityRetentionProvider retention;
    private final Clock clock;

    public AuditRetentionJob(AuditLogRepository logs, ActivityRetentionProvider retention, Clock clock) {
        this.logs = logs;
        this.retention = retention;
        this.clock = clock;
    }

    /** Asks for the retention each time, so a change on the settings page counts from the next night */
    @Scheduled(cron = "0 30 3 * * *", zone = AccessControlProperties.TIME_ZONE)
    @Transactional
    public void run() {
        var days = retention.activityRetentionDays();
        var deleted = logs.deleteOlderThan(clock.instant().minus(Duration.ofDays(days)));
        if (deleted > 0) {
            log.info("Deleted {} activity entries older than {} days", deleted, days);
        }
    }
}
