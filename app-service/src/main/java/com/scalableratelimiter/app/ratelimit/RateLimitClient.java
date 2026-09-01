package com.scalableratelimiter.app.ratelimit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Service
public class RateLimitClient {

    private final RestClient restClient;

    public RateLimitClient(RestClient.Builder restClientBuilder,
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
        } catch (RestClientException e) {
            return RateLimitDecision.UNAVAILABLE;
        }
    }
}
