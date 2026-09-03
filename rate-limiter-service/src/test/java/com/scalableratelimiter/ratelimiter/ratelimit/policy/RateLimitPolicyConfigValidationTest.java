package com.scalableratelimiter.ratelimiter.ratelimit.policy;

import com.scalableratelimiter.ratelimiter.ratelimit.InMemorySlidingWindowLogStateStore;
import com.scalableratelimiter.ratelimiter.ratelimit.MutableSlidingWindowTimeSource;
import com.scalableratelimiter.ratelimiter.ratelimit.RateLimitDecision;
import com.scalableratelimiter.ratelimiter.ratelimit.policy.config.FixedWindowConfig;
import com.scalableratelimiter.ratelimiter.ratelimit.policy.config.SlidingWindowLogConfig;
import com.scalableratelimiter.ratelimiter.ratelimit.policy.config.TokenBucketConfig;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RateLimitPolicyConfigValidationTest {

    @Test
    void fixedWindowConfig_rejectsNonPositiveLimit() {
        assertThrows(IllegalArgumentException.class,
                () -> new FixedWindowConfig(0, Duration.ofMinutes(1)));
    }

    @Test
    void fixedWindowConfig_rejectsNonPositiveWindowDuration() {
        assertThrows(IllegalArgumentException.class,
                () -> new FixedWindowConfig(100, Duration.ZERO));
    }

    @Test
    void slidingWindowLogConfig_rejectsNonPositiveLimit() {
        assertThrows(IllegalArgumentException.class,
                () -> new SlidingWindowLogConfig(0, Duration.ofSeconds(60)));
    }

    @Test
    void slidingWindowLogConfig_rejectsNonPositiveWindowDuration() {
        assertThrows(IllegalArgumentException.class,
                () -> new SlidingWindowLogConfig(100, Duration.ZERO));
    }

    @Test
    void tokenBucketConfig_rejectsNonPositiveCapacity() {
        assertThrows(IllegalArgumentException.class,
                () -> new TokenBucketConfig(0, 1.0));
    }

    @Test
    void tokenBucketConfig_rejectsNonPositiveRefillRate() {
        assertThrows(IllegalArgumentException.class,
                () -> new TokenBucketConfig(10, 0));
    }

    @Test
    void slidingWindowLogPolicy_isImplemented() {
        MutableSlidingWindowTimeSource timeSource = new MutableSlidingWindowTimeSource(1_700_000_000_000L);
        InMemorySlidingWindowLogStateStore store = new InMemorySlidingWindowLogStateStore(timeSource);
        SlidingWindowLogRateLimitPolicy policy = new SlidingWindowLogRateLimitPolicy(
                store,
                new SlidingWindowLogConfig(100, Duration.ofSeconds(60)),
                () -> "member-1");

        assertEquals(RateLimitDecision.ALLOWED, policy.check(new RateLimitContext("alice")));
    }

    @Test
    void tokenBucketPolicy_isNotImplementedYet() {
        TokenBucketRateLimitPolicy policy = new TokenBucketRateLimitPolicy(
                new TokenBucketConfig(100, 1.5));

        assertThrows(UnsupportedOperationException.class,
                () -> policy.check(new RateLimitContext("alice")));
    }
}
