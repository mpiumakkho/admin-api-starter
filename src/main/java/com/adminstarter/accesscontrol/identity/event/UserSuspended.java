package com.adminstarter.accesscontrol.identity.event;

import java.util.UUID;

/** The account was suspended, however that came about: its sessions end */
public record UserSuspended(UUID userId) {}
