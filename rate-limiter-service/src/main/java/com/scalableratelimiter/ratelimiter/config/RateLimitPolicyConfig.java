package com.scalableratelimiter.ratelimiter.config;

import com.scalableratelimiter.ratelimiter.ratelimit.RateLimitStateStore;
import com.scalableratelimiter.ratelimiter.ratelimit.policy.DefaultRateLimitPolicyResolver;
import com.scalableratelimiter.ratelimiter.ratelimit.policy.FixedWindowRateLimitPolicy;
import com.scalableratelimiter.ratelimiter.ratelimit.policy.RateLimitPolicyResolver;
import com.scalableratelimiter.ratelimiter.ratelimit.policy.config.FixedWindowConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class RateLimitPolicyConfig {

    @Bean
    FixedWindowRateLimitPolicy fixedWindowRateLimitPolicy(Clock clock, RateLimitStateStore stateStore) {
        return new FixedWindowRateLimitPolicy(clock, stateStore, FixedWindowConfig.defaults());
    }

    @Bean
    RateLimitPolicyResolver rateLimitPolicyResolver(FixedWindowRateLimitPolicy fixedWindowRateLimitPolicy) {
        return new DefaultRateLimitPolicyResolver(fixedWindowRateLimitPolicy);
    }
}
