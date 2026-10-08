package com.adminstarter.accesscontrol.audit.spi;

/**
 * How long the activity log keeps its entries. AsyncConfig answers it from the settings' activityRetentionDays, so
 * that audit, which only listens (R5), reads a setting without depending on the settings module (P-018).
 */
public interface ActivityRetentionProvider {

    /** Entries older than this many days are deleted every night */
    int activityRetentionDays();
}
