package com.adminstarter.accesscontrol.notification.service;

import com.adminstarter.accesscontrol.authentication.event.NewDeviceSignedIn;
import com.adminstarter.accesscontrol.authorization.dto.RoleResponse;
import com.adminstarter.accesscontrol.authorization.service.RoleService;
import com.adminstarter.accesscontrol.branch.service.BranchService;
import com.adminstarter.accesscontrol.common.dto.FieldChange;
import com.adminstarter.accesscontrol.identity.event.RoleAssignmentAdded;
import com.adminstarter.accesscontrol.identity.event.UserInactiveSuspended;
import com.adminstarter.accesscontrol.identity.event.UserPasswordChanged;
import com.adminstarter.accesscontrol.identity.event.UserUpdated;
import com.adminstarter.accesscontrol.identity.service.UserService;
import com.adminstarter.accesscontrol.notification.service.NotificationService.Text;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Tells people by email what happened to their account (API.md, "Emails about your account"; P-027), once the change
 * has committed, in their language (P-029): a sign-in from a new device, a role given, their email or password changed,
 * the account suspended for not being used. Email for now; another channel, such as LINE, is another NotificationService.
 */
@Service
public class SecurityNoticeListener {

    private final NotificationService notifications;
    private final RecipientResolver recipients;
    private final UserService users;
    private final RoleService roles;
    private final BranchService branches;

    public SecurityNoticeListener(NotificationService notifications, RecipientResolver recipients, UserService users,
            RoleService roles, BranchService branches) {
        this.notifications = notifications;
        this.recipients = recipients;
        this.users = users;
        this.roles = roles;
        this.branches = branches;
    }

    @TransactionalEventListener
    void on(NewDeviceSignedIn event) {
        notifications.sendNotice(recipients.of(event.userId()), "new-device",
                List.of(event.browser(), event.os(), event.ip(), event.at()));
    }

    /** To someone active: a new user hears about their first roles from the invitation */
    @TransactionalEventListener
    void on(RoleAssignmentAdded event) {
        var user = users.get(event.userId());
        var role = changed(event.changes(), "roleId").map(UUID::fromString).flatMap(roles::find).map(RoleResponse::name);
        if (!"active".equals(user.status()) || role.isEmpty()) {
            return;
        }
        var scope = changed(event.changes(), "branchId").map(UUID::fromString).flatMap(branches::nameOf)
                .map(name -> Text.of("mail.role-given.scope.branch", name))
                .orElse(Text.of("mail.role-given.scope.organization"));
        Object from = changed(event.changes(), "startsAt").<Object>map(at -> Text.of("mail.role-given.from", Instant.parse(at)))
                .orElse("");
        var until = changed(event.changes(), "endsAt").map(at -> Text.of("mail.role-given.until", Instant.parse(at)))
                .orElse(Text.of("mail.role-given.for-good"));
        notifications.sendNotice(recipients.of(event.userId()), "role-given", List.of(role.get(), scope, from, until));
    }

    /** To the old address: whoever changed it may not be the owner */
    @TransactionalEventListener
    void on(UserUpdated event) {
        var email = event.changes().stream().filter(change -> "email".equals(change.field())).findFirst();
        if (email.isEmpty() || email.get().from() == null || email.get().to() == null) {
            return;
        }
        notifications.sendNotice(recipients.of(event.userId(), email.get().from()), "email-changed",
                List.of(email.get().from(), email.get().to()));
    }

    /** Not the first password, set from the invitation: they signed in before */
    @TransactionalEventListener
    void on(UserPasswordChanged event) {
        if (users.get(event.userId()).lastActiveAt() == null) {
            return;
        }
        notifications.sendNotice(recipients.of(event.userId()), "password-changed", List.of());
    }

    @TransactionalEventListener
    void on(UserInactiveSuspended event) {
        notifications.sendNotice(recipients.of(event.userId()), "inactive-suspended", List.of(event.days()));
    }

    /** The value a change set, such as the role id of a new assignment */
    private static Optional<String> changed(List<FieldChange> changes, String field) {
        return changes.stream().filter(change -> field.equals(change.field())).findFirst()
                .map(FieldChange::to);
    }
}
