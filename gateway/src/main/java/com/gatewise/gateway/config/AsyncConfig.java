package com.gatewise.gateway.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables {@code @Async} so off-request work (e.g. webhook delivery) doesn't block the call, and
 * {@code @Scheduled} for periodic jobs (e.g. the data-retention sweep).
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {}
