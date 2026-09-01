package com.scalableratelimiter.app.ratelimit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class RateLimitClient {

    private final RestClient restClient;

    public RateLimitClient(RestClient.Builder restClientBuilder,
                           @Value("${rate-limiter.base-url}") String baseUrl) {
        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .build();
    }

    public boolean checkRateLimit(String userId) {
        RateLimitCheckResponse response = restClient.post()
                .uri("/api/rate-limit/check")
                .header(RateLimitFilter.USER_ID_HEADER, userId)
                .retrieve()
                .body(RateLimitCheckResponse.class);

        return response != null && response.allowed();
    }
}
