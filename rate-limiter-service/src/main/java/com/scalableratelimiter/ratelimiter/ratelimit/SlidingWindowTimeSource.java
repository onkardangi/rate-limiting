package com.scalableratelimiter.ratelimiter.ratelimit;

public interface SlidingWindowTimeSource {

    long nowEpochMillis();

    static SlidingWindowTimeSource systemUtc() {
        return () -> System.currentTimeMillis();
    }
}
