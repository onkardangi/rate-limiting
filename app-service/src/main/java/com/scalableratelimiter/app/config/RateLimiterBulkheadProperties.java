package com.scalableratelimiter.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "rate-limiter.bulkhead")
public record RateLimiterBulkheadProperties(
        Integer maxConcurrentCalls,
        Duration maxWaitDuration) {

    public static final int DEFAULT_MAX_CONCURRENT_CALLS = 10;
    public static final Duration DEFAULT_MAX_WAIT_DURATION = Duration.ZERO;

    public RateLimiterBulkheadProperties {
        if (maxConcurrentCalls == null) {
            maxConcurrentCalls = DEFAULT_MAX_CONCURRENT_CALLS;
        }
        if (maxWaitDuration == null) {
            maxWaitDuration = DEFAULT_MAX_WAIT_DURATION;
        }
    }
}
