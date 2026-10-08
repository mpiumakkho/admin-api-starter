package com.adminstarter.accesscontrol.security;

import java.util.UUID;

/**
 * Whether the session an access token names is still open. Implemented by the authentication module, which owns
 * sessions; declared here so that security does not depend on it (authentication already depends on security to
 * issue tokens).
 */
public interface SessionChecker {

    /** Open and belonging to this user; also notes that the session was just used */
    boolean isOpen(UUID sessionId, UUID userId);
}
