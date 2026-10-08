package com.adminstarter.accesscontrol.authentication.event;

import java.time.Instant;
import java.util.UUID;

/** A password reset link was made; {@code token} is the secret for the email, kept nowhere else */
public record PasswordResetIssued(UUID userId, String email, String firstName, String token, Instant expiresAt) {}
