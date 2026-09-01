package com.scalableratelimiter.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "rate-limiter.client")
public record RateLimiterClientProperties(
        Duration connectionTimeout,
        Duration readTimeout) {

    public static final Duration DEFAULT_CONNECTION_TIMEOUT = Duration.ofMillis(500);
    public static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(1);

    public RateLimiterClientProperties {
        if (connectionTimeout == null) {
            connectionTimeout = DEFAULT_CONNECTION_TIMEOUT;
        }
        if (readTimeout == null) {
            readTimeout = DEFAULT_READ_TIMEOUT;
        }
    }
}
