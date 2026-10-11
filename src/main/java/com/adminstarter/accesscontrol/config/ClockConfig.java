package com.adminstarter.accesscontrol.config;

import java.time.Clock;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * One clock for every timestamp and expiry, so tests can move time. It ticks in milliseconds, as JavaScript dates and
 * the app's ISO strings do, so a time the server sets reads back from PostgreSQL (microseconds) and from the app the
 * same, and compares equal; no service needs to round it.
 */
@Configuration
public class ClockConfig {

    @Bean
    Clock clock() {
        return Clock.tick(Clock.systemUTC(), Duration.ofMillis(1));
    }
}
