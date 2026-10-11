package com.adminstarter.accesscontrol.notification.service;

import com.adminstarter.accesscontrol.AccessControlProperties;
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
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Tells people by email what happened to their account (API.md, "Emails about your account"; P-027), once the change
 * has committed: a sign-in from a new device, a role given, their email or password changed, the account suspended for
 * not being used. Email for now; another channel, such as LINE, is another NotificationService.
 */
@Service
public class SecurityNoticeListener {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm 'UTC'").withZone(ZoneOffset.UTC);

    private final NotificationService notifications;
    private final UserService users;
    private final RoleService roles;
    private final BranchService branches;
    private final String productName;

    public SecurityNoticeListener(NotificationService notifications, UserService users, RoleService roles,
            BranchService branches, AccessControlProperties properties) {
        this.notifications = notifications;
        this.users = users;
        this.roles = roles;
        this.branches = branches;
        this.productName = properties.productName();
    }

    @TransactionalEventListener
    void on(NewDeviceSignedIn event) {
        notifications.sendNotice(event.email(), event.firstName(), "New sign-in to " + productName, List.of(
                "Your " + productName + " account was just signed in from a device it was not used on before.",
                "Device: " + event.browser() + " on " + event.os() + ", from " + event.ip() + ", " + WHEN.format(event.at()) + "."));
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
                .map(name -> "in " + name + " only").orElse("for the whole organization");
        var from = changed(event.changes(), "startsAt").map(at -> ", from " + WHEN.format(Instant.parse(at))).orElse("");
        var until = changed(event.changes(), "endsAt").map(at -> ", until " + WHEN.format(Instant.parse(at))).orElse(", for good");
        notifications.sendNotice(user.email(), user.firstName(), "You have a new role in " + productName, List.of(
                "You were given the role " + role.get() + " " + scope + from + until + "."));
    }

    /** To the old address: whoever changed it may not be the owner */
    @TransactionalEventListener
    void on(UserUpdated event) {
        var email = event.changes().stream().filter(change -> "email".equals(change.field())).findFirst();
        if (email.isEmpty() || email.get().from() == null) {
            return;
        }
        var user = users.get(event.userId());
        notifications.sendNotice(String.valueOf(email.get().from()), user.firstName(), "Your " + productName + " email was changed",
                List.of("The email of your " + productName + " account was changed from " + email.get().from() + " to "
                        + email.get().to() + "."));
    }

    /** Not the first password, set from the invitation: they signed in before */
    @TransactionalEventListener
    void on(UserPasswordChanged event) {
        var user = users.get(event.userId());
        if (user.lastActiveAt() == null) {
            return;
        }
        notifications.sendNotice(user.email(), user.firstName(), "Your " + productName + " password was changed",
                List.of("The password of your " + productName + " account was changed."));
    }

    @TransactionalEventListener
    void on(UserInactiveSuspended event) {
        notifications.sendNotice(event.email(), event.firstName(), "Your " + productName + " account was suspended", List.of(
                "Your " + productName + " account was suspended because nobody signed in to it for " + event.days() + " days.",
                "Ask your administrator to restore it if you still need it."));
    }

    /** The value a change set, such as the role id of a new assignment */
    private static Optional<String> changed(List<FieldChange> changes, String field) {
        return changes.stream().filter(change -> field.equals(change.field())).findFirst()
                .map(FieldChange::to);
    }
}
