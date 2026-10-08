package com.adminstarter.accesscontrol.authentication.event;

import java.util.List;
import java.util.UUID;

/** Someone ended sessions from the devices list; {@code devices} names each, as "Chrome · Windows" */
public record SessionsEnded(UUID actorId, UUID userId, String fullName, List<String> devices) {}
