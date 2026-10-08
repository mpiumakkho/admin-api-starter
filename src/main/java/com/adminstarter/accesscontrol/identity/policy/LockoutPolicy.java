package com.adminstarter.accesscontrol.identity.policy;

import com.adminstarter.accesscontrol.AccessControlProperties;
import java.time.Duration;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * How long an account stays locked after wrong passwords or codes in a row: nothing until maxAttempts, then
 * baseDuration, doubling with each further failure up to maxDuration (1m, 2m, 4m … 30m by default).
 */
@Component
public class LockoutPolicy {

    private final int maxAttempts;
    private final Duration baseDuration;
    private final Duration maxDuration;

    public LockoutPolicy(AccessControlProperties properties) {
        this.maxAttempts = properties.lockout().maxAttempts();
        this.baseDuration = properties.lockout().baseDuration();
        this.maxDuration = properties.lockout().maxDuration();
    }

    public Optional<Duration> lockFor(int failedAttempts) {
        if (failedAttempts < maxAttempts) {
            return Optional.empty();
        }
        var doublings = Math.min(failedAttempts - maxAttempts, 20);
        var duration = baseDuration.multipliedBy(1L << doublings);
        return Optional.of(duration.compareTo(maxDuration) > 0 ? maxDuration : duration);
    }
}
