package com.scalableratelimiter.ratelimiter.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimiterServiceInfrastructureTest {

    private static final Instant MINUTE_N_START = Instant.parse("2026-08-31T12:00:00Z");

    @Mock
    private RateLimitStateStore stateStore;

    private RateLimiterService rateLimiterService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(MINUTE_N_START, ZoneOffset.UTC);
        rateLimiterService = new RateLimiterService(clock, stateStore);
    }

    @Test
    void stateStoreSuccessUnderLimit_returnsAllowed() {
        when(stateStore.increment(anyString(), any(Duration.class))).thenReturn(1L);

        assertEquals(RateLimitDecision.ALLOWED, rateLimiterService.checkRequest("alice"));
    }

    @Test
    void quotaExceeded_returnsRateLimited() {
        when(stateStore.increment(anyString(), any(Duration.class)))
                .thenReturn((long) RateLimiterService.MAX_REQUESTS_PER_MINUTE + 1);

        assertEquals(RateLimitDecision.RATE_LIMITED, rateLimiterService.checkRequest("alice"));
    }

    @Test
    void stateStoreInfrastructureFailure_returnsUnavailable() {
        when(stateStore.increment(anyString(), any(Duration.class)))
                .thenThrow(new RateLimitStateStoreException("Redis unavailable"));

        assertEquals(RateLimitDecision.UNAVAILABLE, rateLimiterService.checkRequest("alice"));
    }

    @Test
    void infrastructureFailure_isNotReportedAsRateLimited() {
        when(stateStore.increment(anyString(), any(Duration.class)))
                .thenThrow(new RateLimitStateStoreException("Redis unavailable"));

        assertEquals(RateLimitDecision.UNAVAILABLE, rateLimiterService.checkRequest("alice"));
    }

    @Test
    void infrastructureFailure_isNotReportedAsAllowed() {
        when(stateStore.increment(anyString(), any(Duration.class)))
                .thenThrow(new RateLimitStateStoreException("Redis unavailable"));

        assertEquals(RateLimitDecision.UNAVAILABLE, rateLimiterService.checkRequest("alice"));
    }
}
