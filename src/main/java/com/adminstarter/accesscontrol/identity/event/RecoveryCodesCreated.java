package com.adminstarter.accesscontrol.identity.event;

import java.util.UUID;

/** Someone made new recovery codes, which replaced their old ones (P-025) */
public record RecoveryCodesCreated(UUID userId, String fullName) {}
