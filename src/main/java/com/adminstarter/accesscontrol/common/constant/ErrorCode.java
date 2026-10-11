package com.adminstarter.accesscontrol.common.constant;

import com.adminstarter.accesscontrol.common.exception.AccessControlException;
import java.util.Locale;
import org.springframework.http.HttpStatus;

/**
 * Why a request was refused. The JSON body carries {@link #code()}, which admin-ng-starter turns into a message
 * (AdminApiErrorCode in data/admin-api.ts); the HTTP status follows API.md.
 */
public enum ErrorCode {
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED),
    TWO_FACTOR_REQUIRED(HttpStatus.UNAUTHORIZED),
    INVALID_CODE(HttpStatus.UNAUTHORIZED),
    FORBIDDEN(HttpStatus.FORBIDDEN),
    SELF_CHANGE(HttpStatus.FORBIDDEN),
    ACCOUNT_SUSPENDED(HttpStatus.FORBIDDEN),
    INVITE_PENDING(HttpStatus.FORBIDDEN),
    /** The role requires two-step sign-in (the settings' twoFactorRoleIds) and it is off (P-016) */
    TWO_FACTOR_MANDATORY(HttpStatus.FORBIDDEN),
    /** A risky change, and the person last signed in or confirmed their password too long ago (P-023) */
    REAUTH_REQUIRED(HttpStatus.FORBIDDEN),
    NOT_FOUND(HttpStatus.NOT_FOUND),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST),
    INVALID_TOKEN(HttpStatus.BAD_REQUEST),
    EMAIL_TAKEN(HttpStatus.CONFLICT),
    ROLE_IN_USE(HttpStatus.CONFLICT),
    SYSTEM_ROLE(HttpStatus.CONFLICT),
    NOT_INVITED(HttpStatus.CONFLICT),
    DEFAULT_ROLE(HttpStatus.CONFLICT),
    /** The person already holds the role, now or later, at the same time (P-019) */
    ALREADY_ASSIGNED(HttpStatus.CONFLICT),
    /** Nobody active would keep a role with every permission for good (P-019) */
    LAST_ADMINISTRATOR(HttpStatus.CONFLICT),
    /** Another branch has the code, ignoring case (P-020) */
    CODE_TAKEN(HttpStatus.CONFLICT),
    /** People belong to the branch, or hold a role there now or later (P-020) */
    BRANCH_IN_USE(HttpStatus.CONFLICT),
    /** The branch is closed: nobody new joins it or gets a role there (P-020) */
    BRANCH_CLOSED(HttpStatus.CONFLICT),
    /** The person holds, at the same time, another role that a rule says cannot be held with this one (P-022) */
    ROLE_CONFLICT(HttpStatus.CONFLICT),
    EMAIL_DOMAIN(HttpStatus.UNPROCESSABLE_CONTENT),
    WEAK_PASSWORD(HttpStatus.UNPROCESSABLE_CONTENT),
    /** One of the user's most recent passwords (the settings' passwordHistory, P-017) */
    PASSWORD_REUSED(HttpStatus.UNPROCESSABLE_CONTENT),
    /** Longer than the role's assignment rules allow, or for good when it must end (P-019) */
    ASSIGNMENT_TOO_LONG(HttpStatus.UNPROCESSABLE_CONTENT),
    /** The role asks for a reason, and none was given (P-019) */
    REASON_REQUIRED(HttpStatus.UNPROCESSABLE_CONTENT),
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }

    /** As the client sees it, such as "email_taken" */
    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }

    public AccessControlException exception() {
        return new AccessControlException(this);
    }
}
