package com.scalableratelimiter.ratelimit;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

@Service
public class RateLimiterService {

    static final int MAX_REQUESTS_PER_MINUTE = 100;

    private final Clock clock;
    private final Map<String, UserWindowState> userState = new HashMap<>();

    public RateLimiterService(Clock clock) {
        this.clock = clock;
    }

    public boolean allowRequest(String userId) {
        long currentWindow = currentWindowMinute();

        UserWindowState state = userState.get(userId);

        if (state == null || state.windowMinute() != currentWindow) {
            userState.put(userId, new UserWindowState(currentWindow, 1));
            return true;
        }

        if (state.count() >= MAX_REQUESTS_PER_MINUTE) {
            return false;
        }

        userState.put(userId, new UserWindowState(currentWindow, state.count() + 1));
        return true;
    }

    private long currentWindowMinute() {
        return clock.instant()
                .atZone(clock.getZone())
                .truncatedTo(ChronoUnit.MINUTES)
                .toEpochSecond();
    }
}
