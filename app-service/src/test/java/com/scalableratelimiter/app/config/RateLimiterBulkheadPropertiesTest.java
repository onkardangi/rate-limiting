package com.scalableratelimiter.app.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@TestPropertySource(properties = {
        "rate-limiter.bulkhead.max-concurrent-calls=5",
        "rate-limiter.bulkhead.max-wait-duration=25ms"
})
class RateLimiterBulkheadPropertiesTest {

    @Autowired
    private RateLimiterBulkheadProperties properties;

    @Test
    void bindsConfiguredBulkheadProperties() {
        assertEquals(5, properties.maxConcurrentCalls());
        assertEquals(Duration.ofMillis(25), properties.maxWaitDuration());
    }
}
