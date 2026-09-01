package com.scalableratelimiter.app.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@TestPropertySource(properties = {
        "rate-limiter.client.connection-timeout=250ms",
        "rate-limiter.client.read-timeout=750ms"
})
class RateLimiterClientPropertiesTest {

    @Autowired
    private RateLimiterClientProperties properties;

    @Test
    void bindsConfiguredTimeoutsFromApplicationProperties() {
        assertEquals(Duration.ofMillis(250), properties.connectionTimeout());
        assertEquals(Duration.ofMillis(750), properties.readTimeout());
    }
}
