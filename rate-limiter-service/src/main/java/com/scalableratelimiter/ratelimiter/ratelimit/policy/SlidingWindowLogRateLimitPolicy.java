package com.scalableratelimiter.ratelimiter.ratelimit.policy;

import com.scalableratelimiter.ratelimiter.ratelimit.RateLimitDecision;
import com.scalableratelimiter.ratelimiter.ratelimit.policy.config.SlidingWindowLogConfig;

/**
 * Extension point for exact rolling-window rate limiting.
 *
 * <p>Future behavior for each request at time {@code T}:
 * <ol>
 *   <li>Remove accepted request timestamps {@code <= T - windowDuration}</li>
 *   <li>Count timestamps remaining</li>
 *   <li>If count {@code >= limit}: return {@code RATE_LIMITED}</li>
 *   <li>Otherwise: record {@code T} and return {@code ALLOWED}</li>
 * </ol>
 * The remove → count → decide → add operation must be atomic in Redis, likely via Lua.
 */
public class SlidingWindowLogRateLimitPolicy implements RateLimitPolicy {

    private final SlidingWindowLogConfig config;

    public SlidingWindowLogRateLimitPolicy(SlidingWindowLogConfig config) {
        this.config = config;
    }

    @Override
    public RateLimitDecision check(RateLimitContext context) {
        throw new UnsupportedOperationException(
                "Sliding window log policy is not implemented yet");
    }
}
