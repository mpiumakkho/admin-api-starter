package com.adminstarter.accesscontrol.identity.service;

import com.adminstarter.accesscontrol.common.context.Actor;
import com.adminstarter.accesscontrol.identity.dto.AccountSummary;
import com.adminstarter.accesscontrol.identity.dto.UserResponse;
import java.util.Optional;
import java.util.UUID;

/** What other modules may ask about users */
public interface UserService {

    /** For the check on every request. Cached, evicted when any user changes. */
    Optional<AccountSummary> findAccount(UUID userId);

    Optional<AccountSummary> findAccountByEmail(String email);

    UserResponse get(UUID userId);

    /** Updates lastActiveAt */
    void recordSignIn(UUID userId);

    /** The user set a password from their invitation: an invited account becomes active */
    void acceptInvitation(UUID userId);

    /**
     * Someone whose sessions and two-step sign-in the actor may see and change: themselves, or, with users.edit,
     * someone they manage. Refuses with not_found or forbidden otherwise.
     */
    UserResponse requireSecurityManageable(Actor actor, UUID userId);
}
