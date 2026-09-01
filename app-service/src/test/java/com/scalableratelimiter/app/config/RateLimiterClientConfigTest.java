package com.scalableratelimiter.app.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.client.RestClient;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
class RateLimiterClientConfigTest {

    @Autowired
    private RateLimiterClientProperties properties;

    @Autowired
    @Qualifier("rateLimiterRestClientBuilder")
    private RestClient.Builder rateLimiterRestClientBuilder;

    @Test
    void configuresRateLimiterClientTimeoutsFromProperties() {
        assertEquals(Duration.ofMillis(500), properties.connectionTimeout());
        assertEquals(Duration.ofSeconds(1), properties.readTimeout());
    }

    @Test
    void exposesDedicatedRateLimiterRestClientBuilder() {
        assertNotNull(rateLimiterRestClientBuilder);
    }
}
