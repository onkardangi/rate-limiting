package com.scalableratelimiter.ratelimiter.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Test/local fallback store with concurrency-safe increments for deterministic unit tests.
 */
public class InMemoryRateLimitStateStore implements RateLimitStateStore {

    private final Clock clock;
    private final ConcurrentHashMap<String, Entry> store = new ConcurrentHashMap<>();

    public InMemoryRateLimitStateStore(Clock clock) {
        this.clock = clock;
    }

    @Override
    public long increment(String key, Duration ttl) {
        Entry updated = store.compute(key, (entryKey, existing) -> {
            Instant now = clock.instant();
            if (existing == null || existing.expiresAt().isBefore(now)) {
                return new Entry(1, now.plus(ttl));
            }
            return new Entry(existing.count() + 1, existing.expiresAt());
        });
        return updated.count();
    }

    Duration ttlFor(String key) {
        Entry entry = store.get(key);
        if (entry == null) {
            return null;
        }
        return Duration.between(clock.instant(), entry.expiresAt());
    }

    private record Entry(long count, Instant expiresAt) {
    }
}
