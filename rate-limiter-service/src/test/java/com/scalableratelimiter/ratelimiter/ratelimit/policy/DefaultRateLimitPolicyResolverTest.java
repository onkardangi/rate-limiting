package com.scalableratelimiter.ratelimiter.ratelimit.policy;

import com.scalableratelimiter.ratelimiter.ratelimit.InMemoryRateLimitStateStore;
import com.scalableratelimiter.ratelimiter.ratelimit.policy.config.FixedWindowConfig;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

class DefaultRateLimitPolicyResolverTest {

    @Test
    void resolvesToFixedWindowPolicy() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-31T12:00:00Z"), ZoneOffset.UTC);
        FixedWindowRateLimitPolicy fixedWindowPolicy = new FixedWindowRateLimitPolicy(
                clock,
                new InMemoryRateLimitStateStore(clock),
                FixedWindowConfig.defaults());
        DefaultRateLimitPolicyResolver resolver = new DefaultRateLimitPolicyResolver(fixedWindowPolicy);

        RateLimitPolicy resolved = resolver.resolve(new RateLimitContext("alice"));

        assertSame(fixedWindowPolicy, resolved);
        assertInstanceOf(FixedWindowRateLimitPolicy.class, resolved);
    }

    @Test
    void doesNotResolveToSlidingWindowLogPolicy() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-31T12:00:00Z"), ZoneOffset.UTC);
        FixedWindowRateLimitPolicy fixedWindowPolicy = new FixedWindowRateLimitPolicy(
                clock,
                new InMemoryRateLimitStateStore(clock),
                FixedWindowConfig.defaults());
        DefaultRateLimitPolicyResolver resolver = new DefaultRateLimitPolicyResolver(fixedWindowPolicy);

        RateLimitPolicy resolved = resolver.resolve(new RateLimitContext("alice"));

        assertFalse(resolved instanceof SlidingWindowLogRateLimitPolicy);
    }

    @Test
    void doesNotResolveToTokenBucketPolicy() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-31T12:00:00Z"), ZoneOffset.UTC);
        FixedWindowRateLimitPolicy fixedWindowPolicy = new FixedWindowRateLimitPolicy(
                clock,
                new InMemoryRateLimitStateStore(clock),
                FixedWindowConfig.defaults());
        DefaultRateLimitPolicyResolver resolver = new DefaultRateLimitPolicyResolver(fixedWindowPolicy);

        RateLimitPolicy resolved = resolver.resolve(new RateLimitContext("alice"));

        assertFalse(resolved instanceof TokenBucketRateLimitPolicy);
    }
}
