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
    NOT_FOUND(HttpStatus.NOT_FOUND),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST),
    INVALID_TOKEN(HttpStatus.BAD_REQUEST),
    EMAIL_TAKEN(HttpStatus.CONFLICT),
    ROLE_IN_USE(HttpStatus.CONFLICT),
    SYSTEM_ROLE(HttpStatus.CONFLICT),
    NOT_INVITED(HttpStatus.CONFLICT),
    DEFAULT_ROLE(HttpStatus.CONFLICT),
    EMAIL_DOMAIN(HttpStatus.UNPROCESSABLE_CONTENT),
    WEAK_PASSWORD(HttpStatus.UNPROCESSABLE_CONTENT),
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
