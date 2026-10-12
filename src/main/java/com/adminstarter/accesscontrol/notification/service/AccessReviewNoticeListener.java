package com.adminstarter.accesscontrol.notification.service;

import com.adminstarter.accesscontrol.AccessControlProperties;
import com.adminstarter.accesscontrol.review.event.AccessReviewStarted;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Tells each active person who may decide some of a new access review how many roles wait for them and by when, with a
 * link to it (API.md, "Emails about your account"; P-028), once the review has committed
 */
@Service
public class AccessReviewNoticeListener {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm 'UTC'").withZone(ZoneOffset.UTC);

    private final NotificationService notifications;
    private final String baseUrl;

    public AccessReviewNoticeListener(NotificationService notifications, AccessControlProperties properties) {
        this.notifications = notifications;
        this.baseUrl = properties.mail().baseUrl().replaceAll("/$", "");
    }

    @TransactionalEventListener
    void on(AccessReviewStarted event) {
        var link = baseUrl + "/access-reviews/" + event.reviewId();
        for (var reviewer : event.reviewers()) {
            notifications.sendNotice(reviewer.email(), reviewer.firstName(), "Access review: " + event.name(), List.of(
                    "The access review " + event.name() + " has " + reviewer.assignments()
                            + " role assignments for you to decide, by " + WHEN.format(event.dueAt()) + ".",
                    "For each one, choose Keep or Remove. The roles marked Remove are taken away when the review closes;"
                            + " the ones nobody decided stay as they are.",
                    "Open the review: " + link));
        }
    }
}
