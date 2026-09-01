package com.scalableratelimiter.app.ratelimit;

import com.sun.net.httpserver.HttpServer;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
    private HttpServer slowServer;

    @BeforeEach
    void setUp() {
        restClientBuilder = RestClient.builder();
        rateLimiterServer = MockRestServiceServer.bindTo(restClientBuilder).build();
        rateLimitClient = new RateLimitClient(restClientBuilder, BASE_URL, permissiveCircuitBreaker());
    }

    @AfterEach
    void tearDown() {
        if (slowServer != null) {
            slowServer.stop(0);
            slowServer = null;
        }
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

    @Test
    void checkRateLimit_returnsUnavailable_onConnectionTimeout() {
        RateLimitClient client = clientWithTimeouts(
                Duration.ofMillis(50),
                Duration.ofSeconds(1),
                "http://127.0.0.1:1");

        RateLimitDecision decision = client.checkRateLimit("alice");

        assertEquals(RateLimitDecision.UNAVAILABLE, decision);
        assertNotEquals(RateLimitDecision.ALLOWED, decision);
        assertNotEquals(RateLimitDecision.RATE_LIMITED, decision);
    }

    @Test
    void checkRateLimit_returnsUnavailable_onReadTimeout() throws Exception {
        slowServer = HttpServer.create(new InetSocketAddress(0), 0);
        slowServer.createContext("/api/rate-limit/check", exchange -> {
            try {
                Thread.sleep(2_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            byte[] response = "{\"decision\":\"ALLOWED\"}".getBytes();
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        slowServer.start();

        String baseUrl = "http://localhost:" + slowServer.getAddress().getPort();
        RateLimitClient client = clientWithTimeouts(
                Duration.ofSeconds(1),
                Duration.ofMillis(100),
                baseUrl);

        RateLimitDecision decision = client.checkRateLimit("alice");

        assertEquals(RateLimitDecision.UNAVAILABLE, decision);
        assertNotEquals(RateLimitDecision.ALLOWED, decision);
        assertNotEquals(RateLimitDecision.RATE_LIMITED, decision);
    }

    @Test
    void checkRateLimit_doesNotReturnRateLimited_onTimeout() {
        RateLimitClient client = clientWithTimeouts(
                Duration.ofMillis(50),
                Duration.ofMillis(50),
                "http://127.0.0.1:1");

        assertNotEquals(RateLimitDecision.RATE_LIMITED, client.checkRateLimit("alice"));
    }

    @Test
    void checkRateLimit_doesNotReturnAllowed_onTimeout() {
        RateLimitClient client = clientWithTimeouts(
                Duration.ofMillis(50),
                Duration.ofMillis(50),
                "http://127.0.0.1:1");

        assertNotEquals(RateLimitDecision.ALLOWED, client.checkRateLimit("alice"));
    }

    private static RateLimitClient clientWithTimeouts(
            Duration connectionTimeout,
            Duration readTimeout,
            String baseUrl) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(connectionTimeout)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        return new RateLimitClient(RestClient.builder().requestFactory(requestFactory), baseUrl, permissiveCircuitBreaker());
    }

    static CircuitBreaker permissiveCircuitBreaker() {
        return CircuitBreaker.of("rate-limiter-test-permissive", CircuitBreakerConfig.custom()
                .failureRateThreshold(100)
                .minimumNumberOfCalls(1_000)
                .slidingWindowSize(100)
                .build());
    }
}
