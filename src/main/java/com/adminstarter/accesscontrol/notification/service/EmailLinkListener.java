package com.adminstarter.accesscontrol.notification.service;

import com.adminstarter.accesscontrol.AccessControlProperties;
import com.adminstarter.accesscontrol.authentication.event.InvitationIssued;
import com.adminstarter.accesscontrol.authentication.event.PasswordResetIssued;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Emails each new link once the change that made it has committed, so nobody gets a link that a rollback undid, in the
 * person's language (P-029). The links open the dashboard: {@code /activate?token=…} and {@code /reset-password?token=…}.
 */
@Service
public class EmailLinkListener {

    private final NotificationService notifications;
    private final RecipientResolver recipients;
    private final String baseUrl;

    public EmailLinkListener(NotificationService notifications, RecipientResolver recipients, AccessControlProperties properties) {
        this.notifications = notifications;
        this.recipients = recipients;
        this.baseUrl = properties.mail().baseUrl().replaceAll("/$", "");
    }

    @TransactionalEventListener
    void on(InvitationIssued event) {
        notifications.sendInvitation(recipients.of(event.userId()), link("/activate", event.token()), event.expiresAt());
    }

    @TransactionalEventListener
    void on(PasswordResetIssued event) {
        notifications.sendPasswordReset(recipients.of(event.userId()), link("/reset-password", event.token()), event.expiresAt());
    }

    private String link(String path, String token) {
        return baseUrl + path + "?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
    }
}
