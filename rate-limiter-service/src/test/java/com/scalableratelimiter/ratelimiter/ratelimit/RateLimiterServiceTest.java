package com.scalableratelimiter.ratelimiter.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
        assertEquals(RateLimitDecision.ALLOWED, rateLimiterService.checkRequest("alice"));
    }

    @Test
    void requestsOneThroughOneHundred_areAllowed() {
        for (int i = 0; i < RateLimiterService.MAX_REQUESTS_PER_MINUTE; i++) {
            assertEquals(RateLimitDecision.ALLOWED, rateLimiterService.checkRequest("alice"),
                    "Request " + (i + 1) + " should be allowed");
        }
    }

    @Test
    void requestOneHundredAndOne_inSameMinute_isRejected() {
        for (int i = 0; i < RateLimiterService.MAX_REQUESTS_PER_MINUTE; i++) {
            rateLimiterService.checkRequest("alice");
        }

        assertEquals(RateLimitDecision.RATE_LIMITED, rateLimiterService.checkRequest("alice"));
    }

    @Test
    void differentUsers_haveIndependentCounters() {
        for (int i = 0; i < RateLimiterService.MAX_REQUESTS_PER_MINUTE; i++) {
            rateLimiterService.checkRequest("alice");
        }

        assertEquals(RateLimitDecision.RATE_LIMITED, rateLimiterService.checkRequest("alice"));
        assertEquals(RateLimitDecision.ALLOWED, rateLimiterService.checkRequest("bob"));
    }

    @Test
    void afterMovingToNextMinute_userIsAllowedAgain() {
        for (int i = 0; i < RateLimiterService.MAX_REQUESTS_PER_MINUTE; i++) {
            rateLimiterService.checkRequest("alice");
        }
        assertEquals(RateLimitDecision.RATE_LIMITED, rateLimiterService.checkRequest("alice"));

        clock = Clock.fixed(MINUTE_N_PLUS_ONE, ZoneOffset.UTC);
        stateStore = new InMemoryRateLimitStateStore(clock);
        rateLimiterService = new RateLimiterService(clock, stateStore);

        assertEquals(RateLimitDecision.ALLOWED, rateLimiterService.checkRequest("alice"));
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
            assertEquals(RateLimitDecision.ALLOWED, rateLimiterService.checkRequest("alice"));
        }
        assertEquals(RateLimitDecision.RATE_LIMITED, rateLimiterService.checkRequest("alice"));

        clock = Clock.fixed(MINUTE_N_PLUS_ONE, ZoneOffset.UTC);
        stateStore = new InMemoryRateLimitStateStore(clock);
        rateLimiterService = new RateLimiterService(clock, stateStore);

        for (int i = 0; i < RateLimiterService.MAX_REQUESTS_PER_MINUTE; i++) {
            assertEquals(RateLimitDecision.ALLOWED, rateLimiterService.checkRequest("alice"));
        }
        assertEquals(RateLimitDecision.RATE_LIMITED, rateLimiterService.checkRequest("alice"));
    }
}
