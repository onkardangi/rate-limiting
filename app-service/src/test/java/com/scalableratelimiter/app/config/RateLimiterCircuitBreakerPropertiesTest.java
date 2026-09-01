package com.scalableratelimiter.app.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@TestPropertySource(properties = {
        "rate-limiter.circuit-breaker.failure-rate-threshold=60",
        "rate-limiter.circuit-breaker.sliding-window-size=8",
        "rate-limiter.circuit-breaker.minimum-number-of-calls=4",
        "rate-limiter.circuit-breaker.wait-duration-in-open-state=2s",
        "rate-limiter.circuit-breaker.permitted-calls-in-half-open-state=2"
})
class RateLimiterCircuitBreakerPropertiesTest {

    @Autowired
    private RateLimiterCircuitBreakerProperties properties;

    @Test
    void bindsConfiguredCircuitBreakerProperties() {
        assertEquals(60f, properties.failureRateThreshold());
        assertEquals(8, properties.slidingWindowSize());
        assertEquals(4, properties.minimumNumberOfCalls());
        assertEquals(Duration.ofSeconds(2), properties.waitDurationInOpenState());
        assertEquals(2, properties.permittedCallsInHalfOpenState());
    }
}
