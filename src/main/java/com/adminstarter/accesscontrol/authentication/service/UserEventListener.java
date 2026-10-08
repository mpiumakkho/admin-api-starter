package com.adminstarter.accesscontrol.authentication.service;

import com.adminstarter.accesscontrol.identity.event.UserCreated;
import com.adminstarter.accesscontrol.identity.event.UserInvitationResent;
import com.adminstarter.accesscontrol.identity.event.UserPasswordChanged;
import com.adminstarter.accesscontrol.identity.event.UserPasswordReset;
import com.adminstarter.accesscontrol.identity.event.UserSuspended;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * What changes to a user mean for their sign-ins (API.md): suspending someone, or a new password, ends their
 * sessions; an invitation or a reset makes an email link. Runs in the transaction of the change, so both happen
 * or neither does.
 */
@Service
public class UserEventListener {

    private final SessionService sessions;
    private final ActionTokenService actionTokens;

    public UserEventListener(SessionService sessions, ActionTokenService actionTokens) {
        this.sessions = sessions;
        this.actionTokens = actionTokens;
    }

    @EventListener
    void on(UserCreated event) {
        if (event.invited()) {
            actionTokens.issueInvitation(event.userId(), event.email(), event.firstName());
        }
    }

    @EventListener
    void on(UserInvitationResent event) {
        actionTokens.issueInvitation(event.userId(), event.email(), event.firstName());
    }

    @EventListener
    void on(UserSuspended event) {
        sessions.endOpen(event.userId(), null);
    }

    @EventListener
    void on(UserPasswordReset event) {
        if (!event.self()) {
            sessions.endOpen(event.userId(), null);
        }
        actionTokens.issuePasswordReset(event.userId(), event.email(), event.firstName());
    }

    /** Anyone who knew the old password is signed out everywhere else */
    @EventListener
    void on(UserPasswordChanged event) {
        sessions.endOpen(event.userId(), event.keepSessionId());
    }
}
