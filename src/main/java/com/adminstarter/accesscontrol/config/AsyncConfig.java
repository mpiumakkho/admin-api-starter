package com.adminstarter.accesscontrol.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Work outside the request: emails are sent asynchronously after the change commits, and scheduled jobs prune the
 * activity log and keep the event streams alive. With spring.threads.virtual.enabled, both run on virtual threads.
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {}
