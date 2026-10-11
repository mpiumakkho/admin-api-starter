package com.adminstarter.accesscontrol.authentication.event;

import java.time.Instant;
import java.util.UUID;

/** A sign-in from a browser and system the person never signed in with before, not their first (P-027) */
public record NewDeviceSignedIn(UUID userId, String email, String firstName, String browser, String os, String ip, Instant at) {}
