package com.scalableratelimiter.ratelimiter.config;

import com.scalableratelimiter.ratelimiter.ratelimit.InMemoryRateLimitStateStore;
import com.scalableratelimiter.ratelimiter.ratelimit.InMemorySlidingWindowLogStateStore;
import com.scalableratelimiter.ratelimiter.ratelimit.RateLimitStateStore;
import com.scalableratelimiter.ratelimiter.ratelimit.SlidingWindowLogStateStore;
import com.scalableratelimiter.ratelimiter.ratelimit.SlidingWindowTimeSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class RateLimitStateStoreConfig {

    @Bean
    @ConditionalOnProperty(name = "rate-limit.state-store", havingValue = "memory")
    RateLimitStateStore inMemoryRateLimitStateStore(Clock clock) {
        return new InMemoryRateLimitStateStore(clock);
    }

    @Bean
    @ConditionalOnProperty(name = "rate-limit.state-store", havingValue = "memory")
    SlidingWindowLogStateStore inMemorySlidingWindowLogStateStore() {
        return new InMemorySlidingWindowLogStateStore(SlidingWindowTimeSource.systemUtc());
    }
}
