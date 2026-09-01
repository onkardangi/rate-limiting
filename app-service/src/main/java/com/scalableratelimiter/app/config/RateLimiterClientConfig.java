package com.scalableratelimiter.app.config;

import com.scalableratelimiter.app.ratelimit.RateLimiterDependencyException;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

@Configuration
@EnableConfigurationProperties({
        RateLimiterClientProperties.class,
        RateLimiterCircuitBreakerProperties.class,
        RateLimiterBulkheadProperties.class
})
public class RateLimiterClientConfig {

    private static final Logger log = LoggerFactory.getLogger(RateLimiterClientConfig.class);

    @Bean
    RestClient.Builder rateLimiterRestClientBuilder(RateLimiterClientProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectionTimeout())
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());

        return RestClient.builder().requestFactory(requestFactory);
    }

    @Bean
    CircuitBreaker rateLimiterCircuitBreaker(RateLimiterCircuitBreakerProperties properties) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(properties.failureRateThreshold())
                .slidingWindowSize(properties.slidingWindowSize())
                .minimumNumberOfCalls(properties.minimumNumberOfCalls())
                .waitDurationInOpenState(properties.waitDurationInOpenState())
                .permittedNumberOfCallsInHalfOpenState(properties.permittedCallsInHalfOpenState())
                .recordExceptions(RateLimiterDependencyException.class)
                .ignoreExceptions(BulkheadFullException.class)
                .build();

        CircuitBreaker circuitBreaker = CircuitBreaker.of("rateLimiter", config);
        circuitBreaker.getEventPublisher().onStateTransition(event ->
                log.warn("Rate limiter circuit breaker state transition: {} -> {}",
                        event.getStateTransition().getFromState(),
                        event.getStateTransition().getToState()));
        return circuitBreaker;
    }

    @Bean
    Bulkhead rateLimiterBulkhead(RateLimiterBulkheadProperties properties) {
        BulkheadConfig config = BulkheadConfig.custom()
                .maxConcurrentCalls(properties.maxConcurrentCalls())
                .maxWaitDuration(properties.maxWaitDuration())
                .build();

        return Bulkhead.of("rateLimiter", config);
    }
}
