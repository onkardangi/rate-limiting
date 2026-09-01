package com.scalableratelimiter.ratelimiter.config;

import com.scalableratelimiter.ratelimiter.ratelimit.InMemoryRateLimitStateStore;
import com.scalableratelimiter.ratelimiter.ratelimit.RateLimitStateStore;
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
}
