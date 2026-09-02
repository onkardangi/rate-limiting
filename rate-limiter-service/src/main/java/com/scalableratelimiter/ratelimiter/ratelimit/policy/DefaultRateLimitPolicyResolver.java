package com.scalableratelimiter.ratelimiter.ratelimit.policy;

public class DefaultRateLimitPolicyResolver implements RateLimitPolicyResolver {

    private final RateLimitPolicy defaultPolicy;

    public DefaultRateLimitPolicyResolver(RateLimitPolicy defaultPolicy) {
        this.defaultPolicy = defaultPolicy;
    }

    @Override
    public RateLimitPolicy resolve(RateLimitContext context) {
        return defaultPolicy;
    }
}
