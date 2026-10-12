package com.adminstarter.accesscontrol.review.entity;

import java.util.Locale;

public enum AccessReviewStatus {
    /** Decisions are taken until it closes */
    OPEN,
    /** At its due date, or sooner when someone closed it: the roles decided Remove were taken away */
    CLOSED;

    /** As the API writes it: "open", "closed" */
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }
}
