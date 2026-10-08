package com.adminstarter.accesscontrol.audit.spi;

/**
 * How long the activity log keeps its entries. The settings module implements it from activityRetentionDays, so
 * that audit, which only listens (R5), reads a setting without depending on the settings module (P-018).
 */
public interface ActivityRetentionProvider {

    /** Entries older than this many days are deleted every night */
    int activityRetentionDays();
}
