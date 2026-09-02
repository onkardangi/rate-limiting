package com.scalableratelimiter.ratelimiter.ratelimit.policy;

import com.scalableratelimiter.ratelimiter.ratelimit.RateLimitDecision;
import com.scalableratelimiter.ratelimiter.ratelimit.policy.config.TokenBucketConfig;

/**
 * Extension point for sustained-rate limiting with controlled bursts.
 *
 * <p>Future state: available tokens and last refill time. A future atomic Redis transition will:
 * <ol>
 *   <li>Read consistent current time</li>
 *   <li>Compute elapsed time and refill progress</li>
 *   <li>Cap tokens at bucket capacity</li>
 *   <li>Consume one request cost if available</li>
 *   <li>Persist state and return the decision</li>
 * </ol>
 * Token bucket controls sustained throughput via refill rate and burst allowance via capacity.
 * It does not guarantee an exact rolling-window limit.
 */
public class TokenBucketRateLimitPolicy implements RateLimitPolicy {

    private final TokenBucketConfig config;

    public TokenBucketRateLimitPolicy(TokenBucketConfig config) {
        this.config = config;
    }

    @Override
    public RateLimitDecision check(RateLimitContext context) {
        throw new UnsupportedOperationException("Token bucket policy is not implemented yet");
    }
}
