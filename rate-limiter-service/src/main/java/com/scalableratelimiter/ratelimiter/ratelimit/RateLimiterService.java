package com.scalableratelimiter.ratelimiter.ratelimit;

import com.scalableratelimiter.ratelimiter.ratelimit.policy.RateLimitContext;
import com.scalableratelimiter.ratelimiter.ratelimit.policy.RateLimitPolicyResolver;
import org.springframework.stereotype.Service;

@Service
public class RateLimiterService {

    private final RateLimitPolicyResolver policyResolver;

    public RateLimiterService(RateLimitPolicyResolver policyResolver) {
        this.policyResolver = policyResolver;
    }

    public RateLimitDecision checkRequest(String userId) {
        RateLimitContext context = new RateLimitContext(userId);
        try {
            return policyResolver.resolve(context).check(context);
        } catch (RateLimitStateStoreException e) {
            return RateLimitDecision.UNAVAILABLE;
        }
    }
}
