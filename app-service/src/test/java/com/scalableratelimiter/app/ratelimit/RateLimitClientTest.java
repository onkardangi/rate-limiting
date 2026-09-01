package com.scalableratelimiter.app.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class RateLimitClientTest {

    private static final String BASE_URL = "http://localhost:8081";
    private static final String CHECK_URL = BASE_URL + "/api/rate-limit/check";

    private RestClient.Builder restClientBuilder;
    private MockRestServiceServer rateLimiterServer;
    private RateLimitClient rateLimitClient;

    @BeforeEach
    void setUp() {
        restClientBuilder = RestClient.builder();
        rateLimiterServer = MockRestServiceServer.bindTo(restClientBuilder).build();
        rateLimitClient = new RateLimitClient(restClientBuilder, BASE_URL);
    }

    @Test
    void checkRateLimit_returnsAllowed_whenRateLimiterAllows() {
        rateLimiterServer.expect(requestTo(CHECK_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-User-Id", "alice"))
                .andRespond(withSuccess("{\"decision\":\"ALLOWED\"}", MediaType.APPLICATION_JSON));

        assertEquals(RateLimitDecision.ALLOWED, rateLimitClient.checkRateLimit("alice"));
        rateLimiterServer.verify();
    }

    @Test
    void checkRateLimit_returnsRateLimited_whenRateLimiterDenies() {
        rateLimiterServer.expect(requestTo(CHECK_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-User-Id", "alice"))
                .andRespond(withSuccess("{\"decision\":\"RATE_LIMITED\"}", MediaType.APPLICATION_JSON));

        assertEquals(RateLimitDecision.RATE_LIMITED, rateLimitClient.checkRateLimit("alice"));
        rateLimiterServer.verify();
    }

    @Test
    void checkRateLimit_returnsUnavailable_whenRateLimiterReportsUnavailable() {
        rateLimiterServer.expect(requestTo(CHECK_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-User-Id", "alice"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE)
                        .body("{\"decision\":\"UNAVAILABLE\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertEquals(RateLimitDecision.UNAVAILABLE, rateLimitClient.checkRateLimit("alice"));
        rateLimiterServer.verify();
    }

    @Test
    void checkRateLimit_returnsUnavailable_whenRateLimiterIsUnreachable() {
        rateLimiterServer.expect(requestTo(CHECK_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-User-Id", "alice"))
                .andRespond(withServerError());

        assertEquals(RateLimitDecision.UNAVAILABLE, rateLimitClient.checkRateLimit("alice"));
        rateLimiterServer.verify();
    }
}
