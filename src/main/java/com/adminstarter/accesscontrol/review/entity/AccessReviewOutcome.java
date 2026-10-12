package com.adminstarter.accesscontrol.review.entity;

import java.util.Locale;

/** What closing the review did with one role (API.md, "Access reviews") */
public enum AccessReviewOutcome {
    /** Decided Keep */
    KEPT,
    /** Decided Remove, and taken away */
    REMOVED,
    /** Nobody decided: it stays */
    NOT_REVIEWED,
    /** Decided Remove, but it had ended or been taken away already */
    GONE,
    /** Decided Remove, but nobody else would be left to run the system: it stays */
    LAST_ADMINISTRATOR;

    /** As the API writes it: "kept", "not_reviewed" */
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }
}
