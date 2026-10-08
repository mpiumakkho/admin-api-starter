package com.adminstarter.accesscontrol.audit.service;

import com.adminstarter.accesscontrol.AccessControlProperties;
import com.adminstarter.accesscontrol.audit.repository.AuditLogRepository;
import java.time.Clock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Every night, deletes activity older than access-control.audit.retention-days (two years by default) */
@Service
public class AuditRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(AuditRetentionJob.class);

    private final AuditLogRepository logs;
    private final Clock clock;
    private final Duration retention;

    public AuditRetentionJob(AuditLogRepository logs, Clock clock, AccessControlProperties properties) {
        this.logs = logs;
        this.clock = clock;
        this.retention = Duration.ofDays(properties.audit().retentionDays());
    }

    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void run() {
        var deleted = logs.deleteOlderThan(clock.instant().minus(retention));
        if (deleted > 0) {
            log.info("Deleted {} activity entries older than {} days", deleted, retention.toDays());
        }
    }
}
