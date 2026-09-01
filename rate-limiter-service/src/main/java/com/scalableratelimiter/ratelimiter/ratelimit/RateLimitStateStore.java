package com.scalableratelimiter.ratelimiter.ratelimit;

import java.time.Duration;

public interface RateLimitStateStore {

    /**
     * Atomically increment the counter for the supplied key and return the new value.
     * When the counter is first created, expiration should be configured using the supplied TTL.
     */
    long increment(String key, Duration ttl);
}
