package com.adminstarter.accesscontrol.security;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Whether the session an access token names is still open. Implemented by the authentication module, which owns
 * sessions; declared here so that security does not depend on it (authentication already depends on security to
 * issue tokens).
 */
public interface SessionChecker {

    /**
     * When the person last proved who they are in this session, by signing in or confirming their password (P-023),
     * while it is open and belongs to this user; empty otherwise. Also notes that the session was just used.
     */
    Optional<Instant> verifiedAt(UUID sessionId, UUID userId);
}
