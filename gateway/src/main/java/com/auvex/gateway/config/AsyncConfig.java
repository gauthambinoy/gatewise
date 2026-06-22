package com.auvex.gateway.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/** Enables {@code @Async} so off-request work (e.g. webhook delivery) doesn't block the call. */
@Configuration
@EnableAsync
public class AsyncConfig {}
