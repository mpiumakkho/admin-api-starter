package com.adminstarter.accesscontrol.identity.policy;

import static org.assertj.core.api.Assertions.assertThat;

import com.adminstarter.accesscontrol.AccessControlProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class LockoutPolicyTest {

    private final LockoutPolicy policy = new LockoutPolicy(defaults());

    @Test
    void no_lock_before_five_wrong_attempts() {
        assertThat(policy.lockFor(4)).isEmpty();
    }

    @Test
    void lock_doubles_from_one_minute_up_to_thirty() {
        assertThat(policy.lockFor(5)).contains(Duration.ofMinutes(1));
        assertThat(policy.lockFor(6)).contains(Duration.ofMinutes(2));
        assertThat(policy.lockFor(9)).contains(Duration.ofMinutes(16));
        assertThat(policy.lockFor(10)).contains(Duration.ofMinutes(30));
        assertThat(policy.lockFor(500)).contains(Duration.ofMinutes(30));
    }

    /** AccessControlProperties with every default, as an empty application.yml gives */
    static AccessControlProperties defaults() {
        return new Binder(new MapConfigurationPropertySource())
                .bindOrCreate("access-control", AccessControlProperties.class);
    }
}
