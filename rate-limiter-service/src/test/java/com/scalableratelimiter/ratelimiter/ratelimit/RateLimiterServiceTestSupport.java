package com.scalableratelimiter.ratelimiter.ratelimit;

import com.scalableratelimiter.ratelimiter.ratelimit.policy.DefaultRateLimitPolicyResolver;
import com.scalableratelimiter.ratelimiter.ratelimit.policy.FixedWindowRateLimitPolicy;
import com.scalableratelimiter.ratelimiter.ratelimit.policy.RateLimitPolicyResolver;
import com.scalableratelimiter.ratelimiter.ratelimit.policy.config.FixedWindowConfig;

import java.time.Clock;

final class RateLimiterServiceTestSupport {

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
}
