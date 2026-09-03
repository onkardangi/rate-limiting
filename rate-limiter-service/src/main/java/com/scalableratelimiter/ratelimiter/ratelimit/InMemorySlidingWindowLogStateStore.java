package com.scalableratelimiter.ratelimiter.ratelimit;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Concurrency-safe in-memory sliding-window log for deterministic unit tests.
 * Uses an injectable time source so tests can control rolling-window boundaries.
 */
public class InMemorySlidingWindowLogStateStore implements SlidingWindowLogStateStore {

    private final SlidingWindowTimeSource timeSource;
    private final ConcurrentHashMap<String, KeyState> store = new ConcurrentHashMap<>();

    public InMemorySlidingWindowLogStateStore(SlidingWindowTimeSource timeSource) {
        this.timeSource = timeSource;
    }

    @Override
    public boolean tryAccept(String key,
                               int limit,
                               Duration windowDuration,
                               Duration keyTtl,
                               String uniqueMember) {
        KeyState state = store.computeIfAbsent(key, ignored -> new KeyState());
        synchronized (state) {
            long nowMs = timeSource.nowEpochMillis();
            long windowStartMs = nowMs - windowDuration.toMillis();

            state.removeExpired(windowStartMs);

            if (state.acceptedCount() >= limit) {
                return false;
            }

            state.addAccepted(nowMs, uniqueMember);
            state.expiresAt = Instant.ofEpochMilli(nowMs).plus(keyTtl);
            return true;
        }
    }

    public int acceptedCount(String key) {
        KeyState state = store.get(key);
        return state == null ? 0 : state.acceptedCount();
    }

    public Duration ttlFor(String key) {
        KeyState state = store.get(key);
        if (state == null || state.expiresAt == null) {
            return null;
        }
        return Duration.between(Instant.ofEpochMilli(timeSource.nowEpochMillis()), state.expiresAt);
    }

    boolean containsMember(String key, String member) {
        KeyState state = store.get(key);
        return state != null && state.containsMember(member);
    }

    private static final class KeyState {
        private final List<AcceptedEntry> acceptedEntries = new ArrayList<>();
        private Instant expiresAt;

        void removeExpired(long windowStartMs) {
            acceptedEntries.removeIf(entry -> entry.timestampMs() <= windowStartMs);
        }

        int acceptedCount() {
            return acceptedEntries.size();
        }

        void addAccepted(long timestampMs, String member) {
            acceptedEntries.add(new AcceptedEntry(timestampMs, member));
        }

        boolean containsMember(String member) {
            return acceptedEntries.stream().anyMatch(entry -> entry.member().equals(member));
        }
    }

    private record AcceptedEntry(long timestampMs, String member) {
    }
}
