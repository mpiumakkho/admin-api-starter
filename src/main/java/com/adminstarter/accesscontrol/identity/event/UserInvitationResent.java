package com.adminstarter.accesscontrol.identity.event;

import java.util.UUID;

public record UserInvitationResent(UUID actorId, UUID userId, String fullName, String email, String firstName) {}
