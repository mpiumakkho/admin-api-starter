package com.adminstarter.accesscontrol.identity.event;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * The user set a new password, signed in or from an email link. Their other sessions end; {@code keepSessionId}
 * is the one they made the change from, if any.
 */
public record UserPasswordChanged(UUID userId, String fullName, @Nullable UUID keepSessionId) {}
