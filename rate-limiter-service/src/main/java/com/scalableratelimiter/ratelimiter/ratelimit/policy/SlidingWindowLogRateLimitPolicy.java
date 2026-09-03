package com.scalableratelimiter.ratelimiter.ratelimit.policy;

import com.scalableratelimiter.ratelimiter.ratelimit.RateLimitDecision;
import com.scalableratelimiter.ratelimiter.ratelimit.SlidingWindowLogStateStore;
import com.scalableratelimiter.ratelimiter.ratelimit.policy.config.SlidingWindowLogConfig;

/**
 * Exact rolling-window rate limiting using a per-identity log of accepted requests.
 *
 * <p>Boundary semantics at time {@code T}: accepted requests in the interval
 * {@code (T - windowDuration, T]} count toward the limit. A request exactly
 * {@code windowDuration} old no longer counts.
 */
public class SlidingWindowLogRateLimitPolicy implements RateLimitPolicy {

    public static final String KEY_PREFIX = "rate-limit:sliding:";

    private final SlidingWindowLogStateStore stateStore;
    private final SlidingWindowLogConfig config;
    private final SlidingWindowMemberIdSupplier memberIdSupplier;

    public SlidingWindowLogRateLimitPolicy(SlidingWindowLogStateStore stateStore,
                                           SlidingWindowLogConfig config) {
        this(stateStore, config, SlidingWindowMemberIdSupplier.randomUuid());
    }

    public SlidingWindowLogRateLimitPolicy(SlidingWindowLogStateStore stateStore,
                                           SlidingWindowLogConfig config,
                                           SlidingWindowMemberIdSupplier memberIdSupplier) {
        this.stateStore = stateStore;
        this.config = config;
        this.memberIdSupplier = memberIdSupplier;
    }

    @Override
    public RateLimitDecision check(RateLimitContext context) {
        String key = buildKey(context.identity());
        boolean allowed = stateStore.tryAccept(
                key,
                config.limit(),
                config.windowDuration(),
                config.keyTtl(),
                memberIdSupplier.next());

        return allowed ? RateLimitDecision.ALLOWED : RateLimitDecision.RATE_LIMITED;
    }

    public static String buildKey(String identity) {
        return KEY_PREFIX + identity;
    }
}
