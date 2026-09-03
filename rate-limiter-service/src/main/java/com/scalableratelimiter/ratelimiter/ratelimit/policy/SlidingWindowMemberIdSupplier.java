package com.scalableratelimiter.ratelimiter.ratelimit.policy;

import java.util.UUID;

@FunctionalInterface
public interface SlidingWindowMemberIdSupplier {

    String next();

    static SlidingWindowMemberIdSupplier randomUuid() {
        return () -> UUID.randomUUID().toString();
    }
}
