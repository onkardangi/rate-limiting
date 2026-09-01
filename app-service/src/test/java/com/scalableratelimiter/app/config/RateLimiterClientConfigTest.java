package com.scalableratelimiter.app.config;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
class RateLimiterClientConfigTest {

    @Autowired
    private RateLimiterClientProperties clientProperties;

    @Autowired
    private RateLimiterCircuitBreakerProperties circuitBreakerProperties;

    @Autowired
    private RateLimiterBulkheadProperties bulkheadProperties;

    @Autowired
    private CircuitBreaker rateLimiterCircuitBreaker;

    @Autowired
    private Bulkhead rateLimiterBulkhead;

    @Test
    void configuresRateLimiterClientTimeoutsFromProperties() {
        assertEquals(Duration.ofMillis(500), clientProperties.connectionTimeout());
        assertEquals(Duration.ofSeconds(1), clientProperties.readTimeout());
    }

    @Test
    void configuresCircuitBreakerFromProperties() {
        assertEquals(50f, circuitBreakerProperties.failureRateThreshold());
        assertEquals(10, circuitBreakerProperties.slidingWindowSize());
        assertEquals(5, circuitBreakerProperties.minimumNumberOfCalls());
        assertEquals(Duration.ofSeconds(10), circuitBreakerProperties.waitDurationInOpenState());
        assertEquals(3, circuitBreakerProperties.permittedCallsInHalfOpenState());
        assertNotNull(rateLimiterCircuitBreaker);
    }

    @Test
    void configuresBulkheadFromProperties() {
        assertEquals(10, bulkheadProperties.maxConcurrentCalls());
        assertEquals(Duration.ZERO, bulkheadProperties.maxWaitDuration());
        assertNotNull(rateLimiterBulkhead);
    }
}
