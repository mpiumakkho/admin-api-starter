package com.adminstarter.accesscontrol.authentication.event;

import java.util.UUID;

public record SignedIn(UUID userId, String fullName) {}
