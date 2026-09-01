package com.scalableratelimiter.app.ratelimit;

public class RateLimiterDependencyException extends RuntimeException {

    public RateLimiterDependencyException(String message) {
        super(message);
    }

    public RateLimiterDependencyException(String message, Throwable cause) {
        super(message, cause);
    }
}
