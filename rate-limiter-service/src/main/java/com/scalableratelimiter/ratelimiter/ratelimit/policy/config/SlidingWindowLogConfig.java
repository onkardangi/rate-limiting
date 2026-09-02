package com.scalableratelimiter.ratelimiter.ratelimit.policy.config;

import java.time.Duration;

public record SlidingWindowLogConfig(int limit, Duration windowDuration) {

    public SlidingWindowLogConfig {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be greater than zero");
        }
        if (windowDuration == null || windowDuration.isZero() || windowDuration.isNegative()) {
            throw new IllegalArgumentException("windowDuration must be positive");
        }
    }
}
