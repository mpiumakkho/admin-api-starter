package com.adminstarter.accesscontrol.authentication.event;

import java.util.UUID;

/**
 * A refused sign-in, or password confirmation, of an account that exists (P-026). {@code cause}: password, code,
 * locked, suspended or invited.
 */
public record SignInFailed(UUID userId, String fullName, String cause) {}
