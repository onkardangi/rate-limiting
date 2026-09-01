package com.scalableratelimiter.ratelimiter.ratelimit;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.temporal.ChronoUnit;

@Service
public class RateLimiterService {

    static final int MAX_REQUESTS_PER_MINUTE = 100;
    static final Duration WINDOW_KEY_TTL = Duration.ofMinutes(2);
    static final String KEY_PREFIX = "rate-limit:";

    private final Clock clock;
    private final RateLimitStateStore stateStore;

    public RateLimiterService(Clock clock, RateLimitStateStore stateStore) {
        this.clock = clock;
        this.stateStore = stateStore;
    }

    public RateLimitDecision checkRequest(String userId) {
        try {
            long currentWindow = currentWindowMinute();
            String key = buildKey(userId, currentWindow);

            long count = stateStore.increment(key, WINDOW_KEY_TTL);
            return count <= MAX_REQUESTS_PER_MINUTE
                    ? RateLimitDecision.ALLOWED
                    : RateLimitDecision.RATE_LIMITED;
        } catch (RateLimitStateStoreException e) {
            return RateLimitDecision.UNAVAILABLE;
        }
    }

    static String buildKey(String userId, long windowMinute) {
        return KEY_PREFIX + userId + ":" + windowMinute;
    }

    private long currentWindowMinute() {
        return clock.instant()
                .atZone(clock.getZone())
                .truncatedTo(ChronoUnit.MINUTES)
                .toEpochSecond();
    }
}
