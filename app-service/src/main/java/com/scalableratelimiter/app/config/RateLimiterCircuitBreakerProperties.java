package com.scalableratelimiter.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "rate-limiter.circuit-breaker")
public record RateLimiterCircuitBreakerProperties(
        Float failureRateThreshold,
        Integer slidingWindowSize,
        Integer minimumNumberOfCalls,
        Duration waitDurationInOpenState,
        Integer permittedCallsInHalfOpenState) {

    public static final float DEFAULT_FAILURE_RATE_THRESHOLD = 50f;
    public static final int DEFAULT_SLIDING_WINDOW_SIZE = 10;
    public static final int DEFAULT_MINIMUM_NUMBER_OF_CALLS = 5;
    public static final Duration DEFAULT_WAIT_DURATION_IN_OPEN_STATE = Duration.ofSeconds(10);
    public static final int DEFAULT_PERMITTED_CALLS_IN_HALF_OPEN_STATE = 3;

    public RateLimiterCircuitBreakerProperties {
        if (failureRateThreshold == null) {
            failureRateThreshold = DEFAULT_FAILURE_RATE_THRESHOLD;
        }
        if (slidingWindowSize == null) {
            slidingWindowSize = DEFAULT_SLIDING_WINDOW_SIZE;
        }
        if (minimumNumberOfCalls == null) {
            minimumNumberOfCalls = DEFAULT_MINIMUM_NUMBER_OF_CALLS;
        }
        if (waitDurationInOpenState == null) {
            waitDurationInOpenState = DEFAULT_WAIT_DURATION_IN_OPEN_STATE;
        }
        if (permittedCallsInHalfOpenState == null) {
            permittedCallsInHalfOpenState = DEFAULT_PERMITTED_CALLS_IN_HALF_OPEN_STATE;
        }
    }
}
