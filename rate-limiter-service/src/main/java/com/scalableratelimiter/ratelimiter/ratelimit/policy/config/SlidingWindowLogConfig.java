package com.scalableratelimiter.ratelimiter.ratelimit.policy.config;

import java.time.Duration;

public record SlidingWindowLogConfig(int limit, Duration windowDuration) {

    public static final int DEFAULT_LIMIT = 100;
    public static final Duration DEFAULT_WINDOW_DURATION = Duration.ofSeconds(60);

    public SlidingWindowLogConfig {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be greater than zero");
        }
        if (windowDuration == null || windowDuration.isZero() || windowDuration.isNegative()) {
            throw new IllegalArgumentException("windowDuration must be positive");
        }
    }

    public static SlidingWindowLogConfig defaults() {
        return new SlidingWindowLogConfig(DEFAULT_LIMIT, DEFAULT_WINDOW_DURATION);
    }

    public Duration keyTtl() {
        return windowDuration.multipliedBy(2);
    }
}
