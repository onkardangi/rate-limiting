package com.scalableratelimiter.ratelimiter.ratelimit.policy.config;

public record TokenBucketConfig(int capacity, double refillRatePerSecond) {

    public TokenBucketConfig {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be greater than zero");
        }
        if (refillRatePerSecond <= 0) {
            throw new IllegalArgumentException("refillRatePerSecond must be greater than zero");
        }
    }
}
