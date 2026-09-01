package com.scalableratelimiter.app.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;

@Service
public class RateLimitClient {

    private static final Logger log = LoggerFactory.getLogger(RateLimitClient.class);

    private final RestClient restClient;

    public RateLimitClient(@Qualifier("rateLimiterRestClientBuilder") RestClient.Builder restClientBuilder,
                           @Value("${rate-limiter.base-url}") String baseUrl) {
        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .build();
    }

    public RateLimitDecision checkRateLimit(String userId) {
        try {
            ResponseEntity<RateLimitCheckResponse> response = restClient.post()
                    .uri("/api/rate-limit/check")
                    .header(RateLimitFilter.USER_ID_HEADER, userId)
                    .retrieve()
                    .toEntity(RateLimitCheckResponse.class);

            RateLimitCheckResponse body = response.getBody();
            if (body != null && body.decision() != null) {
                return body.decision();
            }
            return RateLimitDecision.UNAVAILABLE;
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == HttpStatus.SERVICE_UNAVAILABLE.value()
                    || e.getStatusCode().is5xxServerError()) {
                return RateLimitDecision.UNAVAILABLE;
            }
            throw e;
        } catch (ResourceAccessException e) {
            if (isTimeout(e)) {
                log.warn("Rate limiter check timed out for user {}", userId);
            } else {
                log.warn("Rate limiter check failed for user {}: {}", userId, e.getMessage());
            }
            return RateLimitDecision.UNAVAILABLE;
        } catch (RestClientException e) {
            log.warn("Rate limiter check failed for user {}: {}", userId, e.getMessage());
            return RateLimitDecision.UNAVAILABLE;
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
