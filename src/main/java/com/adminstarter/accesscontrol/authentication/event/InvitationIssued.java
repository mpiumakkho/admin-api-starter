package com.adminstarter.accesscontrol.authentication.event;

import java.time.Instant;
import java.util.UUID;

/** An invitation link was made; {@code token} is the secret for the email, kept nowhere else */
public record InvitationIssued(UUID userId, String email, String firstName, String token, Instant expiresAt) {}
