package com.scalableratelimiter.app.ratelimit;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

import static io.github.resilience4j.circuitbreaker.CircuitBreaker.State.CLOSED;
import static io.github.resilience4j.circuitbreaker.CircuitBreaker.State.HALF_OPEN;
import static io.github.resilience4j.circuitbreaker.CircuitBreaker.State.OPEN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class RateLimitClientCircuitBreakerTest {

    private static final String BASE_URL = "http://localhost:8081";
    private static final String CHECK_URL = BASE_URL + "/api/rate-limit/check";

    private RestClient.Builder restClientBuilder;
    private MockRestServiceServer rateLimiterServer;
    private CircuitBreaker circuitBreaker;
    private RateLimitClient rateLimitClient;

    @BeforeEach
    void setUp() {
        restClientBuilder = RestClient.builder();
        rateLimiterServer = MockRestServiceServer.bindTo(restClientBuilder).build();
        circuitBreaker = testCircuitBreaker();
        rateLimitClient = new RateLimitClient(restClientBuilder, BASE_URL, circuitBreaker);
    }

    @Test
    void allowedResponses_doNotOpenCircuit() {
        for (int i = 0; i < 6; i++) {
            rateLimiterServer.expect(requestTo(CHECK_URL))
                    .andExpect(method(HttpMethod.POST))
                    .andExpect(header("X-User-Id", "alice"))
                    .andRespond(withSuccess("{\"decision\":\"ALLOWED\"}", MediaType.APPLICATION_JSON));
        }

        for (int i = 0; i < 6; i++) {
            assertEquals(RateLimitDecision.ALLOWED, rateLimitClient.checkRateLimit("alice"));
        }

        assertEquals(CLOSED, circuitBreaker.getState());
        rateLimiterServer.verify();
    }

    @Test
    void rateLimitedResponses_doNotOpenCircuit() {
        for (int i = 0; i < 6; i++) {
            rateLimiterServer.expect(requestTo(CHECK_URL))
                    .andExpect(method(HttpMethod.POST))
                    .andExpect(header("X-User-Id", "alice"))
                    .andRespond(withSuccess("{\"decision\":\"RATE_LIMITED\"}", MediaType.APPLICATION_JSON));
        }

        for (int i = 0; i < 6; i++) {
            assertEquals(RateLimitDecision.RATE_LIMITED, rateLimitClient.checkRateLimit("alice"));
        }

        assertEquals(CLOSED, circuitBreaker.getState());
        rateLimiterServer.verify();
    }

    @Test
    void repeatedDependencyFailures_openCircuit() {
        for (int i = 0; i < 4; i++) {
            rateLimiterServer.expect(requestTo(CHECK_URL))
                    .andExpect(method(HttpMethod.POST))
                    .andExpect(header("X-User-Id", "alice"))
                    .andRespond(withServerError());
        }

        for (int i = 0; i < 4; i++) {
            assertEquals(RateLimitDecision.UNAVAILABLE, rateLimitClient.checkRateLimit("alice"));
        }

        assertEquals(OPEN, circuitBreaker.getState());
        rateLimiterServer.verify();
    }

    @Test
    void openCircuit_shortCircuitsWithoutHttpCall() {
        openCircuitWithFailures();

        assertEquals(RateLimitDecision.UNAVAILABLE, rateLimitClient.checkRateLimit("alice"));
        rateLimiterServer.verify();
    }

    @Test
    void openCircuit_mapsToUnavailable() {
        openCircuitWithFailures();

        assertEquals(RateLimitDecision.UNAVAILABLE, rateLimitClient.checkRateLimit("alice"));
        assertNotEquals(RateLimitDecision.ALLOWED, rateLimitClient.checkRateLimit("alice"));
        assertNotEquals(RateLimitDecision.RATE_LIMITED, rateLimitClient.checkRateLimit("alice"));
    }

    @Test
    void halfOpenState_permitsProbeAfterOpenWait() {
        openCircuitWithFailures();
        circuitBreaker.transitionToHalfOpenState();

        rateLimiterServer.expect(requestTo(CHECK_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-User-Id", "alice"))
                .andRespond(withSuccess("{\"decision\":\"ALLOWED\"}", MediaType.APPLICATION_JSON));

        assertEquals(RateLimitDecision.ALLOWED, rateLimitClient.checkRateLimit("alice"));
        assertEquals(CLOSED, circuitBreaker.getState());
        rateLimiterServer.verify();
    }

    @Test
    void successfulProbe_recoversToClosed() {
        openCircuitWithFailures();
        circuitBreaker.transitionToHalfOpenState();

        rateLimiterServer.expect(requestTo(CHECK_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-User-Id", "alice"))
                .andRespond(withSuccess("{\"decision\":\"ALLOWED\"}", MediaType.APPLICATION_JSON));

        rateLimitClient.checkRateLimit("alice");

        assertEquals(CLOSED, circuitBreaker.getState());
    }

    @Test
    void failedProbe_reopensCircuit() {
        openCircuitWithFailures();
        circuitBreaker.transitionToHalfOpenState();

        rateLimiterServer.expect(requestTo(CHECK_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-User-Id", "alice"))
                .andRespond(withServerError());

        assertEquals(RateLimitDecision.UNAVAILABLE, rateLimitClient.checkRateLimit("alice"));
        assertEquals(OPEN, circuitBreaker.getState());
    }

    @Test
    void timeoutCountsAsDependencyFailure_andCanOpenCircuit() {
        RateLimitClient timeoutClient = new RateLimitClient(
                RestClient.builder().requestFactory(requestFactoryWithReadTimeout(Duration.ofMillis(50))),
                "http://127.0.0.1:1",
                testCircuitBreaker());

        for (int i = 0; i < 4; i++) {
            assertEquals(RateLimitDecision.UNAVAILABLE, timeoutClient.checkRateLimit("alice"));
        }

        assertEquals(OPEN, timeoutClient.circuitBreaker().getState());
    }

    @Test
    void circuitBreakerDoesNotRetryFailedCalls() {
        rateLimiterServer.expect(requestTo(CHECK_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-User-Id", "alice"))
                .andRespond(withServerError());

        assertEquals(RateLimitDecision.UNAVAILABLE, rateLimitClient.checkRateLimit("alice"));
        rateLimiterServer.verify();
    }

    private void openCircuitWithFailures() {
        for (int i = 0; i < 4; i++) {
            rateLimiterServer.expect(requestTo(CHECK_URL))
                    .andExpect(method(HttpMethod.POST))
                    .andExpect(header("X-User-Id", "alice"))
                    .andRespond(withServerError());
        }

        for (int i = 0; i < 4; i++) {
            rateLimitClient.checkRateLimit("alice");
        }

        assertEquals(OPEN, circuitBreaker.getState());
        rateLimiterServer.verify();
        rateLimiterServer.reset();
    }

    private static CircuitBreaker testCircuitBreaker() {
        return CircuitBreaker.of("rate-limiter-test", CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .slidingWindowSize(4)
                .minimumNumberOfCalls(4)
                .waitDurationInOpenState(Duration.ofMillis(100))
                .permittedNumberOfCallsInHalfOpenState(1)
                .recordExceptions(RateLimiterDependencyException.class)
                .build());
    }

    private static JdkClientHttpRequestFactory requestFactoryWithReadTimeout(Duration readTimeout) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(50))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        return requestFactory;
    }
}
