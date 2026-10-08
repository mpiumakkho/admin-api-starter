package com.adminstarter.accesscontrol.common.context;

import java.util.Optional;

/**
 * Where the current request came from, bound by RequestContextFilter for the length of the request. The activity
 * log and new sessions read it; code running outside a request (startup, scheduled jobs) sees none.
 */
public record RequestContext(String requestId, String ip, String browser, String os) {

    private static final ScopedValue<RequestContext> CURRENT = ScopedValue.newInstance();

    /** For work outside a request */
    public static final RequestContext NONE = new RequestContext("", "", "", "");

    public static Optional<RequestContext> current() {
        return CURRENT.isBound() ? Optional.of(CURRENT.get()) : Optional.empty();
    }

    public static RequestContext currentOrNone() {
        return current().orElse(NONE);
    }

    /** Runs {@code operation} with this context bound */
    public <R, X extends Throwable> R run(ScopedValue.CallableOp<R, X> operation) throws X {
        return ScopedValue.where(CURRENT, this).call(operation);
    }

    public Device device() {
        return new Device(browser, os);
    }
}
