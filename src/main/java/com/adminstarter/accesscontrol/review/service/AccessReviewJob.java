package com.adminstarter.accesscontrol.review.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Every hour, closes the access reviews whose due date has passed (P-028), taking away the roles decided Remove; the
 * activity log says the server did it. Two instances never close the same review twice: each locks it first, and the
 * second finds it closed.
 */
@Service
public class AccessReviewJob {

    private static final Logger log = LoggerFactory.getLogger(AccessReviewJob.class);

    private final AccessReviewService reviews;

    public AccessReviewJob(AccessReviewService reviews) {
        this.reviews = reviews;
    }

    @Scheduled(cron = "0 5 * * * *")
    public void run() {
        var closed = reviews.closeDue();
        if (closed > 0) {
            log.info("Closed {} access reviews on their due date", closed);
        }
    }
}
