package com.scalableratelimiter.ratelimiter.ratelimit;

public class RateLimitStateStoreException extends RuntimeException {

    public RateLimitStateStoreException(String message) {
        super(message);
    }

    public RateLimitStateStoreException(String message, Throwable cause) {
        super(message, cause);
    }
}
