package com.adminstarter.accesscontrol.identity.event;

import java.util.UUID;

public record TwoFactorChanged(UUID actorId, UUID userId, String fullName, boolean enabled, String reason) {}
