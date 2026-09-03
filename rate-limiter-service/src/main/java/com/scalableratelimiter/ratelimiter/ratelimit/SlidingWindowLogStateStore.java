package com.scalableratelimiter.ratelimiter.ratelimit;

import java.time.Duration;

/**
 * Atomic sliding-window log state operations for exact rolling-window rate limiting.
 * Separate from {@link RateLimitStateStore} so fixed-window increment semantics stay focused.
 */
public interface SlidingWindowLogStateStore {

    /**
     * Atomically attempts to accept a request into the sliding window log.
     *
     * @param key             Redis key for this identity's accepted-request log
     * @param limit           maximum accepted requests allowed in the rolling window
     * @param windowDuration  rolling window size
     * @param keyTtl          garbage-collection TTL for inactive keys
     * @param uniqueMember    unique sorted-set member for this accepted request
     * @return {@code true} when the request is accepted and recorded; {@code false} when rate limited
     */
    boolean tryAccept(String key,
                      int limit,
                      Duration windowDuration,
                      Duration keyTtl,
                      String uniqueMember);
}
