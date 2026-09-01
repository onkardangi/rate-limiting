package com.scalableratelimiter.app.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
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
    void checkRateLimit_returnsTrue_whenRateLimiterAllows() {
        rateLimiterServer.expect(requestTo(CHECK_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-User-Id", "alice"))
                .andRespond(withSuccess("{\"allowed\":true}", MediaType.APPLICATION_JSON));

        assertTrue(rateLimitClient.checkRateLimit("alice"));
        rateLimiterServer.verify();
    }

    @Test
    void checkRateLimit_returnsFalse_whenRateLimiterDenies() {
        rateLimiterServer.expect(requestTo(CHECK_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-User-Id", "alice"))
                .andRespond(withSuccess("{\"allowed\":false}", MediaType.APPLICATION_JSON));

        assertFalse(rateLimitClient.checkRateLimit("alice"));
        rateLimiterServer.verify();
    }
}
