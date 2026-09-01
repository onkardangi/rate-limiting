package com.scalableratelimiter.app.ratelimit;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static io.github.resilience4j.circuitbreaker.CircuitBreaker.State.CLOSED;
import static io.github.resilience4j.circuitbreaker.CircuitBreaker.State.OPEN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class RateLimitClientBulkheadTest {

    private static final String BASE_URL = "http://localhost:8081";
    private static final String CHECK_URL = BASE_URL + "/api/rate-limit/check";

    private ExecutorService executor;

    @AfterEach
    void tearDown() {
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    @Test
    void callsProceedWhenBulkheadCapacityIsAvailable() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        server.expect(requestTo(CHECK_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-User-Id", "alice"))
                .andRespond(withSuccess("{\"decision\":\"ALLOWED\"}", MediaType.APPLICATION_JSON));

        RateLimitClient client = clientWithBuilder(builder, bulkhead(2), RateLimitClientTest.permissiveCircuitBreaker());

        assertEquals(RateLimitDecision.ALLOWED, client.checkRateLimit("alice"));
        server.verify();
    }

    @Test
    void concurrentCallsUpToConfiguredCapacityArePermitted() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        BlockingResponses blocking = configureBlockingResponses(server, 2);
        RateLimitClient client = clientWithBuilder(builder, bulkhead(2), RateLimitClientTest.permissiveCircuitBreaker());
        executor = Executors.newFixedThreadPool(2);

        Future<RateLimitDecision> first = executor.submit(() -> client.checkRateLimit("alice"));
        Future<RateLimitDecision> second = executor.submit(() -> client.checkRateLimit("bob"));

        assertTrue(blocking.requestsStarted().await(5, TimeUnit.SECONDS));
        assertEquals(2, blocking.requestCount().get());

        blocking.releaseResponses().countDown();

        assertEquals(RateLimitDecision.ALLOWED, first.get(5, TimeUnit.SECONDS));
        assertEquals(RateLimitDecision.ALLOWED, second.get(5, TimeUnit.SECONDS));
        server.verify();
    }

    @Test
    void callsBeyondCapacityAreRejectedQuickly() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        BlockingResponses blocking = configureBlockingResponses(server, 2);
        RateLimitClient client = clientWithBuilder(builder, bulkhead(2), RateLimitClientTest.permissiveCircuitBreaker());
        executor = Executors.newFixedThreadPool(2);

        executor.submit(() -> client.checkRateLimit("alice"));
        executor.submit(() -> client.checkRateLimit("bob"));
        assertTrue(blocking.requestsStarted().await(5, TimeUnit.SECONDS));

        long startNanos = System.nanoTime();
        assertEquals(RateLimitDecision.UNAVAILABLE, client.checkRateLimit("charlie"));
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);

        assertTrue(elapsedMillis < 500, "bulkhead rejection should be immediate, took " + elapsedMillis + "ms");

        blocking.releaseResponses().countDown();
        server.verify();
    }

    @Test
    void rejectedCallDoesNotInvokeHttpDependency() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        BlockingResponses blocking = configureBlockingResponses(server, 1);
        RateLimitClient client = clientWithBuilder(builder, bulkhead(1), RateLimitClientTest.permissiveCircuitBreaker());
        executor = Executors.newFixedThreadPool(1);

        executor.submit(() -> client.checkRateLimit("alice"));
        assertTrue(blocking.requestsStarted().await(5, TimeUnit.SECONDS));

        assertEquals(RateLimitDecision.UNAVAILABLE, client.checkRateLimit("bob"));
        assertEquals(1, blocking.requestCount().get());
        server.verify();

        blocking.releaseResponses().countDown();
    }

    @Test
    void bulkheadRejectionMapsToUnavailable() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        BlockingResponses blocking = configureBlockingResponses(server, 1);
        RateLimitClient client = clientWithBuilder(builder, bulkhead(1), RateLimitClientTest.permissiveCircuitBreaker());
        executor = Executors.newFixedThreadPool(1);

        executor.submit(() -> client.checkRateLimit("alice"));
        assertTrue(blocking.requestsStarted().await(5, TimeUnit.SECONDS));

        assertEquals(RateLimitDecision.UNAVAILABLE, client.checkRateLimit("bob"));

        blocking.releaseResponses().countDown();
        server.verify();
    }

    @Test
    void bulkheadRejectionDoesNotMapToRateLimited() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        BlockingResponses blocking = configureBlockingResponses(server, 1);
        RateLimitClient client = clientWithBuilder(builder, bulkhead(1), RateLimitClientTest.permissiveCircuitBreaker());
        executor = Executors.newFixedThreadPool(1);

        executor.submit(() -> client.checkRateLimit("alice"));
        assertTrue(blocking.requestsStarted().await(5, TimeUnit.SECONDS));

        assertNotEquals(RateLimitDecision.RATE_LIMITED, client.checkRateLimit("bob"));

        blocking.releaseResponses().countDown();
        server.verify();
    }

    @Test
    void bulkheadRejectionDoesNotCountAsCircuitBreakerFailure() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        BlockingResponses blocking = configureBlockingResponses(server, 1);
        CircuitBreaker circuitBreaker = testCircuitBreaker();
        RateLimitClient client = clientWithBuilder(builder, bulkhead(1), circuitBreaker);
        executor = Executors.newFixedThreadPool(1);

        executor.submit(() -> client.checkRateLimit("alice"));
        assertTrue(blocking.requestsStarted().await(5, TimeUnit.SECONDS));

        for (int i = 0; i < 10; i++) {
            assertEquals(RateLimitDecision.UNAVAILABLE, client.checkRateLimit("rejected-" + i));
        }

        assertEquals(CLOSED, circuitBreaker.getState());

        blocking.releaseResponses().countDown();
        server.verify();
    }

    @Test
    void circuitBreakerOpenStillShortCircuitsCorrectly() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        CircuitBreaker circuitBreaker = testCircuitBreaker();
        circuitBreaker.transitionToOpenState();
        RateLimitClient client = clientWithBuilder(builder, bulkhead(10), circuitBreaker);

        assertEquals(RateLimitDecision.UNAVAILABLE, client.checkRateLimit("alice"));
        assertEquals(OPEN, circuitBreaker.getState());
        server.verify();
    }

    @Test
    void timeoutBehaviorStillWorksWithBulkhead() {
        RateLimitClient client = clientWithTimeouts(
                bulkhead(5),
                RateLimitClientTest.permissiveCircuitBreaker(),
                Duration.ofMillis(50),
                Duration.ofMillis(50));

        assertEquals(RateLimitDecision.UNAVAILABLE, client.checkRateLimit("alice"));
        assertNotEquals(RateLimitDecision.RATE_LIMITED, client.checkRateLimit("alice"));
    }

    @Test
    void bulkheadDoesNotRetryFailedCalls() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        server.expect(requestTo(CHECK_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-User-Id", "alice"))
                .andRespond(withServerError());

        RateLimitClient client = clientWithBuilder(builder, bulkhead(2), RateLimitClientTest.permissiveCircuitBreaker());

        assertEquals(RateLimitDecision.UNAVAILABLE, client.checkRateLimit("alice"));
        server.verify();
    }

    @Test
    void permitsAreReleasedAfterSuccessfulCompletion() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        Bulkhead bulkhead = bulkhead(1);

        server.expect(requestTo(CHECK_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-User-Id", "alice"))
                .andRespond(withSuccess("{\"decision\":\"ALLOWED\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(CHECK_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-User-Id", "bob"))
                .andRespond(withSuccess("{\"decision\":\"ALLOWED\"}", MediaType.APPLICATION_JSON));

        RateLimitClient client = clientWithBuilder(builder, bulkhead, RateLimitClientTest.permissiveCircuitBreaker());

        assertEquals(RateLimitDecision.ALLOWED, client.checkRateLimit("alice"));
        assertEquals(RateLimitDecision.ALLOWED, client.checkRateLimit("bob"));
        server.verify();
    }

    @Test
    void permitsAreReleasedAfterFailedCompletion() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        Bulkhead bulkhead = bulkhead(1);

        server.expect(requestTo(CHECK_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-User-Id", "alice"))
                .andRespond(withServerError());
        server.expect(requestTo(CHECK_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-User-Id", "bob"))
                .andRespond(withSuccess("{\"decision\":\"ALLOWED\"}", MediaType.APPLICATION_JSON));

        RateLimitClient client = clientWithBuilder(builder, bulkhead, RateLimitClientTest.permissiveCircuitBreaker());

        assertEquals(RateLimitDecision.UNAVAILABLE, client.checkRateLimit("alice"));
        assertEquals(RateLimitDecision.ALLOWED, client.checkRateLimit("bob"));
        server.verify();
    }

    @Test
    void permitsAreReleasedAfterTimedOutCompletion() {
        Bulkhead bulkhead = bulkhead(1);
        RateLimitClient timeoutClient = clientWithTimeouts(
                bulkhead,
                RateLimitClientTest.permissiveCircuitBreaker(),
                Duration.ofMillis(50),
                Duration.ofMillis(50));

        assertEquals(RateLimitDecision.UNAVAILABLE, timeoutClient.checkRateLimit("alice"));

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(CHECK_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-User-Id", "bob"))
                .andRespond(withSuccess("{\"decision\":\"ALLOWED\"}", MediaType.APPLICATION_JSON));

        RateLimitClient successClient = clientWithBuilder(builder, bulkhead, RateLimitClientTest.permissiveCircuitBreaker());

        assertEquals(RateLimitDecision.ALLOWED, successClient.checkRateLimit("bob"));
        server.verify();
    }

    private BlockingResponses configureBlockingResponses(MockRestServiceServer server, int slots) {
        CountDownLatch requestsStarted = new CountDownLatch(slots);
        CountDownLatch releaseResponses = new CountDownLatch(1);
        AtomicInteger requestCount = new AtomicInteger();

        for (int i = 0; i < slots; i++) {
            server.expect(requestTo(CHECK_URL))
                    .andExpect(method(HttpMethod.POST))
                    .andRespond(request -> {
                        requestCount.incrementAndGet();
                        requestsStarted.countDown();
                        try {
                            if (!releaseResponses.await(10, TimeUnit.SECONDS)) {
                                throw new IllegalStateException("timed out waiting to release blocked response");
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException("interrupted while waiting to release blocked response", e);
                        }
                        return withSuccess("{\"decision\":\"ALLOWED\"}", MediaType.APPLICATION_JSON)
                                .createResponse(request);
                    });
        }

        return new BlockingResponses(requestsStarted, releaseResponses, requestCount);
    }

    private RateLimitClient clientWithBuilder(
            RestClient.Builder builder,
            Bulkhead bulkhead,
            CircuitBreaker circuitBreaker) {
        return new RateLimitClient(builder, BASE_URL, circuitBreaker, bulkhead);
    }

    private RateLimitClient clientWithTimeouts(
            Bulkhead bulkhead,
            CircuitBreaker circuitBreaker,
            Duration connectionTimeout,
            Duration readTimeout) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(connectionTimeout)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        return new RateLimitClient(RestClient.builder().requestFactory(requestFactory),
                "http://127.0.0.1:1", circuitBreaker, bulkhead);
    }

    private static Bulkhead bulkhead(int maxConcurrentCalls) {
        return Bulkhead.of("bulkhead-" + UUID.randomUUID(), BulkheadConfig.custom()
                .maxConcurrentCalls(maxConcurrentCalls)
                .maxWaitDuration(Duration.ZERO)
                .build());
    }

    private static CircuitBreaker testCircuitBreaker() {
        return CircuitBreaker.of("cb-" + UUID.randomUUID(), CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .slidingWindowSize(4)
                .minimumNumberOfCalls(4)
                .recordExceptions(RateLimiterDependencyException.class)
                .ignoreExceptions(io.github.resilience4j.bulkhead.BulkheadFullException.class)
                .build());
    }

    private record BlockingResponses(
            CountDownLatch requestsStarted,
            CountDownLatch releaseResponses,
            AtomicInteger requestCount) {
    }
}
