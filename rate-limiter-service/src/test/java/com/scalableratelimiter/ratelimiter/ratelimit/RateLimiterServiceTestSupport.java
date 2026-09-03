package com.scalableratelimiter.ratelimiter.ratelimit;

import com.scalableratelimiter.ratelimiter.ratelimit.policy.DefaultRateLimitPolicyResolver;
import com.scalableratelimiter.ratelimiter.ratelimit.policy.FixedWindowRateLimitPolicy;
import com.scalableratelimiter.ratelimiter.ratelimit.policy.RateLimitPolicyResolver;
import com.scalableratelimiter.ratelimiter.ratelimit.policy.SlidingWindowLogRateLimitPolicy;
import com.scalableratelimiter.ratelimiter.ratelimit.policy.config.FixedWindowConfig;
import com.scalableratelimiter.ratelimiter.ratelimit.policy.config.SlidingWindowLogConfig;

import java.time.Clock;

public final class RateLimiterServiceTestSupport {

    private RateLimiterServiceTestSupport() {
    }

    static RateLimiterService service(Clock clock, RateLimitStateStore stateStore) {
        return service(clock, stateStore, FixedWindowConfig.defaults());
    }

    static RateLimiterService service(Clock clock,
                                      RateLimitStateStore stateStore,
                                      FixedWindowConfig config) {
        FixedWindowRateLimitPolicy policy = new FixedWindowRateLimitPolicy(clock, stateStore, config);
        RateLimitPolicyResolver resolver = new DefaultRateLimitPolicyResolver(policy);
        return new RateLimiterService(resolver);
    }

    public static RateLimiterService slidingWindowService(SlidingWindowLogStateStore stateStore,
                                                   SlidingWindowLogConfig config) {
        SlidingWindowLogRateLimitPolicy policy = new SlidingWindowLogRateLimitPolicy(stateStore, config);
        RateLimitPolicyResolver resolver = new DefaultRateLimitPolicyResolver(policy);
        return new RateLimiterService(resolver);
    }
}
