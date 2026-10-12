package com.adminstarter.accesscontrol.review.entity;

import java.util.Locale;

/** What a reviewer decided about one role */
public enum AccessReviewDecision {
    KEEP,
    REMOVE;

    /** As the API writes it: "keep", "remove" */
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static AccessReviewDecision of(String value) {
        return valueOf(value.toUpperCase(Locale.ROOT));
    }
}
