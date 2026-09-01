package com.scalableratelimiter.app.ratelimit;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;

@Service
public class RateLimitClient {

    private static final Logger log = LoggerFactory.getLogger(RateLimitClient.class);

    private final RestClient restClient;
    private final CircuitBreaker circuitBreaker;

    public RateLimitClient(@Qualifier("rateLimiterRestClientBuilder") RestClient.Builder restClientBuilder,
                           @Value("${rate-limiter.base-url}") String baseUrl,
                           CircuitBreaker rateLimiterCircuitBreaker) {
        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .build();
        this.circuitBreaker = rateLimiterCircuitBreaker;
    }

    public RateLimitDecision checkRateLimit(String userId) {
        try {
            return circuitBreaker.executeSupplier(() -> invokeRateLimiter(userId));
        } catch (CallNotPermittedException e) {
            log.warn("Rate limiter circuit breaker is open; skipping remote call for user {}", userId);
            return RateLimitDecision.UNAVAILABLE;
        } catch (RateLimiterDependencyException e) {
            return RateLimitDecision.UNAVAILABLE;
        }
    }

    CircuitBreaker circuitBreaker() {
        return circuitBreaker;
    }

    private RateLimitDecision invokeRateLimiter(String userId) {
        try {
            ResponseEntity<RateLimitCheckResponse> response = restClient.post()
                    .uri("/api/rate-limit/check")
                    .header(RateLimitFilter.USER_ID_HEADER, userId)
                    .retrieve()
                    .toEntity(RateLimitCheckResponse.class);

            RateLimitCheckResponse body = response.getBody();
            if (body != null && body.decision() != null) {
                if (body.decision() == RateLimitDecision.ALLOWED
                        || body.decision() == RateLimitDecision.RATE_LIMITED) {
                    return body.decision();
                }
                throw new RateLimiterDependencyException(
                        "Rate limiter returned decision: " + body.decision());
            }
            throw new RateLimiterDependencyException("Rate limiter returned an empty decision");
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().is5xxServerError()) {
                throw new RateLimiterDependencyException(
                        "Rate limiter returned HTTP " + e.getStatusCode().value(), e);
            }
            throw e;
        } catch (ResourceAccessException e) {
            if (isTimeout(e)) {
                log.warn("Rate limiter check timed out for user {}", userId);
            } else {
                log.warn("Rate limiter check failed for user {}: {}", userId, e.getMessage());
            }
            throw new RateLimiterDependencyException("Rate limiter call failed", e);
        }
    }

    private static boolean isTimeout(Throwable throwable) {
        while (throwable != null) {
            if (throwable instanceof HttpTimeoutException
                    || throwable instanceof HttpConnectTimeoutException
                    || throwable instanceof java.net.SocketTimeoutException) {
                return true;
            }
            throwable = throwable.getCause();
        }
        return false;
    }
}
