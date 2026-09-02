package com.scalableratelimiter.ratelimiter.ratelimit.policy;

public interface RateLimitPolicyResolver {

    RateLimitPolicy resolve(RateLimitContext context);
}
