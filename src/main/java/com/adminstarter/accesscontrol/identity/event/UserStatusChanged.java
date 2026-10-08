package com.adminstarter.accesscontrol.identity.event;

import java.util.UUID;

/** Suspended or activated from the users list, or activated by accepting an invitation */
public record UserStatusChanged(UUID actorId, UUID userId, String fullName, String from, String to) {}
