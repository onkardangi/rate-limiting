package com.scalableratelimiter.ratelimiter.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimiterServiceTest {

    private static final Instant MINUTE_N_START = Instant.parse("2026-08-31T12:00:00Z");
    private static final Instant MINUTE_N_END = Instant.parse("2026-08-31T12:00:59Z");
    private static final Instant MINUTE_N_PLUS_ONE = Instant.parse("2026-08-31T12:01:00Z");

    private Clock clock;
    private InMemoryRateLimitStateStore stateStore;
    private RateLimiterService rateLimiterService;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(MINUTE_N_START, ZoneOffset.UTC);
        stateStore = new InMemoryRateLimitStateStore(clock);
        rateLimiterService = new RateLimiterService(clock, stateStore);
    }

    @Test
    void firstRequestFromUser_isAllowed() {
        assertTrue(rateLimiterService.allowRequest("alice"));
    }

    @Test
    void requestsOneThroughOneHundred_areAllowed() {
        for (int i = 0; i < RateLimiterService.MAX_REQUESTS_PER_MINUTE; i++) {
            assertTrue(rateLimiterService.allowRequest("alice"),
                    "Request " + (i + 1) + " should be allowed");
        }
    }

    @Test
    void requestOneHundredAndOne_inSameMinute_isRejected() {
        for (int i = 0; i < RateLimiterService.MAX_REQUESTS_PER_MINUTE; i++) {
            rateLimiterService.allowRequest("alice");
        }

        assertFalse(rateLimiterService.allowRequest("alice"));
    }

    @Test
    void differentUsers_haveIndependentCounters() {
        for (int i = 0; i < RateLimiterService.MAX_REQUESTS_PER_MINUTE; i++) {
            rateLimiterService.allowRequest("alice");
        }

        assertFalse(rateLimiterService.allowRequest("alice"));
        assertTrue(rateLimiterService.allowRequest("bob"));
    }

    @Test
    void afterMovingToNextMinute_userIsAllowedAgain() {
        for (int i = 0; i < RateLimiterService.MAX_REQUESTS_PER_MINUTE; i++) {
            rateLimiterService.allowRequest("alice");
        }
        assertFalse(rateLimiterService.allowRequest("alice"));

        clock = Clock.fixed(MINUTE_N_PLUS_ONE, ZoneOffset.UTC);
        stateStore = new InMemoryRateLimitStateStore(clock);
        rateLimiterService = new RateLimiterService(clock, stateStore);

        assertTrue(rateLimiterService.allowRequest("alice"));
    }

    /**
     * Fixed-window trade-off: a user may use their full quota at the end of one minute
     * and immediately use another full quota at the start of the next minute.
     * This is expected under fixed calendar-minute semantics, not a rolling 60-second window.
     */
    @Test
    void fixedWindowBoundary_allowsBurstAcrossMinuteBoundary() {
        clock = Clock.fixed(MINUTE_N_END, ZoneOffset.UTC);
        stateStore = new InMemoryRateLimitStateStore(clock);
        rateLimiterService = new RateLimiterService(clock, stateStore);

        for (int i = 0; i < RateLimiterService.MAX_REQUESTS_PER_MINUTE; i++) {
            assertTrue(rateLimiterService.allowRequest("alice"));
        }
        assertFalse(rateLimiterService.allowRequest("alice"));

        clock = Clock.fixed(MINUTE_N_PLUS_ONE, ZoneOffset.UTC);
        stateStore = new InMemoryRateLimitStateStore(clock);
        rateLimiterService = new RateLimiterService(clock, stateStore);

        for (int i = 0; i < RateLimiterService.MAX_REQUESTS_PER_MINUTE; i++) {
            assertTrue(rateLimiterService.allowRequest("alice"));
        }
        assertFalse(rateLimiterService.allowRequest("alice"));
    }
}
