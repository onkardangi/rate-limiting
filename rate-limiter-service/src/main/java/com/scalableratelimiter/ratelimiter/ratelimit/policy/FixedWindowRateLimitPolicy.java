package com.scalableratelimiter.ratelimiter.ratelimit.policy;

import com.scalableratelimiter.ratelimiter.ratelimit.RateLimitDecision;
import com.scalableratelimiter.ratelimiter.ratelimit.RateLimitStateStore;
import com.scalableratelimiter.ratelimiter.ratelimit.policy.config.FixedWindowConfig;

import java.time.Clock;
import java.time.temporal.ChronoUnit;

public class FixedWindowRateLimitPolicy implements RateLimitPolicy {

    static final String KEY_PREFIX = "rate-limit:";

    private final Clock clock;
    private final RateLimitStateStore stateStore;
    private final FixedWindowConfig config;

    public FixedWindowRateLimitPolicy(Clock clock,
                                      RateLimitStateStore stateStore,
                                      FixedWindowConfig config) {
        this.clock = clock;
        this.stateStore = stateStore;
        this.config = config;
    }

    @Override
    public RateLimitDecision check(RateLimitContext context) {
        long currentWindow = currentWindowStartEpochSecond();
        String key = buildKey(context.identity(), currentWindow);

        long count = stateStore.increment(key, config.keyTtl());
        return count <= config.limit()
                ? RateLimitDecision.ALLOWED
                : RateLimitDecision.RATE_LIMITED;
    }

    public static String buildKey(String identity, long windowStartEpochSecond) {
        return KEY_PREFIX + identity + ":" + windowStartEpochSecond;
    }

    private long currentWindowStartEpochSecond() {
        return clock.instant()
                .atZone(clock.getZone())
                .truncatedTo(ChronoUnit.MINUTES)
                .toEpochSecond();
    }
}
