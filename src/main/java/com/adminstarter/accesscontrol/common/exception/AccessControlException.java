package com.adminstarter.accesscontrol.common.exception;

import com.adminstarter.accesscontrol.common.constant.ErrorCode;

/** A request the rules refuse; ApiExceptionHandler answers it with the code's status and {@code { "code": … }}. */
public class AccessControlException extends RuntimeException {

    private final ErrorCode code;

    public AccessControlException(ErrorCode code) {
        // Refusals are expected answers, not failures: no stack trace
        super(code.code(), null, false, false);
        this.code = code;
    }

    public ErrorCode code() {
        return code;
    }
}
