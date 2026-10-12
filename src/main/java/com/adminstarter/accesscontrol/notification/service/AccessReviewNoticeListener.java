package com.adminstarter.accesscontrol.notification.service;

import com.adminstarter.accesscontrol.AccessControlProperties;
import com.adminstarter.accesscontrol.review.event.AccessReviewStarted;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Tells each active person who may decide some of a new access review how many roles wait for them and by when, with a
 * link to it (API.md, "Emails about your account"; P-028), once the review has committed, in their language (P-029)
 */
@Service
public class AccessReviewNoticeListener {

    private final NotificationService notifications;
    private final RecipientResolver recipients;
    private final String baseUrl;

    public AccessReviewNoticeListener(NotificationService notifications, RecipientResolver recipients,
            AccessControlProperties properties) {
        this.notifications = notifications;
        this.recipients = recipients;
        this.baseUrl = properties.mail().baseUrl().replaceAll("/$", "");
    }

    @TransactionalEventListener
    void on(AccessReviewStarted event) {
        var link = baseUrl + "/access-reviews/" + event.reviewId();
        for (var reviewer : event.reviewers()) {
            notifications.sendNotice(recipients.of(reviewer.userId()), "access-review",
                    List.of(event.name(), reviewer.assignments(), event.dueAt(), link));
        }
    }
}
