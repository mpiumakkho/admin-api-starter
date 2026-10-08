package com.adminstarter.accesscontrol.audit.event;

import java.util.UUID;

/** A change was written to the activity log; every change is, so this is the one signal that data changed */
public record ActivityRecorded(UUID activityId, String action) {}
