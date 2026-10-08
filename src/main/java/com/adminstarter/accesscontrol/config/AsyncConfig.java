package com.adminstarter.accesscontrol.config;

import com.adminstarter.accesscontrol.audit.spi.ActivityRetentionProvider;
import com.adminstarter.accesscontrol.settings.service.SettingsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Work outside the request: emails are sent asynchronously after the change commits, and scheduled jobs prune the
 * activity log and keep the event streams alive. With spring.threads.virtual.enabled, both run on virtual threads.
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {

    /**
     * How long the nightly AuditRetentionJob keeps activity: activityRetentionDays from the settings. Joined here,
     * because audit only listens (R5) and settings must not depend on audit, whose listeners hear its events (R8,
     * P-018).
     */
    @Bean
    ActivityRetentionProvider activityRetention(SettingsService settings) {
        return () -> settings.current().activityRetentionDays();
    }
}
