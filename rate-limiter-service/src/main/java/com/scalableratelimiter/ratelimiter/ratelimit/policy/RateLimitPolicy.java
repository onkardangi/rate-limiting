package com.scalableratelimiter.ratelimiter.ratelimit.policy;

import com.scalableratelimiter.ratelimiter.ratelimit.RateLimitDecision;

public interface RateLimitPolicy {

    RateLimitDecision check(RateLimitContext context);
}
