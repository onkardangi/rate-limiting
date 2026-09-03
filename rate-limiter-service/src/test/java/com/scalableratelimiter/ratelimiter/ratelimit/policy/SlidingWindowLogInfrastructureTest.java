package com.scalableratelimiter.ratelimiter.ratelimit.policy;

import com.scalableratelimiter.ratelimiter.ratelimit.RateLimitDecision;
import com.scalableratelimiter.ratelimiter.ratelimit.RateLimiterService;
import com.scalableratelimiter.ratelimiter.ratelimit.RateLimiterServiceTestSupport;
import com.scalableratelimiter.ratelimiter.ratelimit.RateLimitStateStoreException;
import com.scalableratelimiter.ratelimiter.ratelimit.SlidingWindowLogStateStore;
import com.scalableratelimiter.ratelimiter.ratelimit.policy.config.SlidingWindowLogConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SlidingWindowLogInfrastructureTest {

    @Mock
    private SlidingWindowLogStateStore failingStore;

    @Test
    void storeFailure_surfacesAsUnavailableThroughService() {
        when(failingStore.tryAccept(anyString(), anyInt(), any(Duration.class), any(Duration.class), anyString()))
                .thenThrow(new RateLimitStateStoreException("Redis unavailable"));

        RateLimiterService service = RateLimiterServiceTestSupport.slidingWindowService(
                failingStore,
                SlidingWindowLogConfig.defaults());

        assertEquals(RateLimitDecision.UNAVAILABLE, service.checkRequest("alice"));
    }
}
