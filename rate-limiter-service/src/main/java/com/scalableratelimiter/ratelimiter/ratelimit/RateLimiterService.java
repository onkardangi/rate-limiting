package com.scalableratelimiter.ratelimiter.ratelimit;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RateLimiterService {

    static final int MAX_REQUESTS_PER_MINUTE = 100;

    private final Clock clock;
    private final ConcurrentHashMap<String, UserWindowState> userState = new ConcurrentHashMap<>();

    public RateLimiterService(Clock clock) {
        this.clock = clock;
    }

    /**
     * The rate-limit invariant spans both the current window and the count.
     * A plain thread-safe map is not enough: the read-inspect-increment transition
     * must be atomic per user so concurrent requests cannot observe stale state.
     */
    public boolean allowRequest(String userId) {
        long currentWindow = currentWindowMinute();
        boolean[] allowed = new boolean[1];

        userState.compute(userId, (key, state) -> {
            if (state == null || state.windowMinute() != currentWindow) {
                allowed[0] = true;
                return new UserWindowState(currentWindow, 1);
            }

            if (state.count() >= MAX_REQUESTS_PER_MINUTE) {
                allowed[0] = false;
                return state;
            }

            allowed[0] = true;
            return new UserWindowState(currentWindow, state.count() + 1);
        });

        return allowed[0];
    }

    private long currentWindowMinute() {
        return clock.instant()
                .atZone(clock.getZone())
                .truncatedTo(ChronoUnit.MINUTES)
                .toEpochSecond();
    }
}
