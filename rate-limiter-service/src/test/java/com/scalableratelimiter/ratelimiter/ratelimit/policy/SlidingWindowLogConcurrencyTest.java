package com.scalableratelimiter.ratelimiter.ratelimit.policy;

import com.scalableratelimiter.ratelimiter.ratelimit.InMemorySlidingWindowLogStateStore;
import com.scalableratelimiter.ratelimiter.ratelimit.MutableSlidingWindowTimeSource;
import com.scalableratelimiter.ratelimiter.ratelimit.RateLimitDecision;
import com.scalableratelimiter.ratelimiter.ratelimit.RateLimiterService;
import com.scalableratelimiter.ratelimiter.ratelimit.RateLimiterServiceTestSupport;
import com.scalableratelimiter.ratelimiter.ratelimit.policy.config.SlidingWindowLogConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlidingWindowLogConcurrencyTest {

    private MutableSlidingWindowTimeSource timeSource;
    private InMemorySlidingWindowLogStateStore stateStore;
    private AtomicInteger memberSequence;

    @BeforeEach
    void setUp() {
        timeSource = new MutableSlidingWindowTimeSource(1_700_000_000_000L);
        stateStore = new InMemorySlidingWindowLogStateStore(timeSource);
        memberSequence = new AtomicInteger();
    }

    @Test
    void whenNinetyNineAccepted_twoConcurrentRequests_allowExactlyOneMore() throws Exception {
        SlidingWindowLogConfig config = new SlidingWindowLogConfig(100, Duration.ofSeconds(60));
        SlidingWindowLogRateLimitPolicy policy = policy(config);
        RateLimiterService service = RateLimiterServiceTestSupport.slidingWindowService(stateStore, config);

        for (int i = 0; i < 99; i++) {
            assertEquals(RateLimitDecision.ALLOWED, service.checkRequest("alice"));
        }

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2);
        CountDownLatch doneLatch = new CountDownLatch(2);
        AtomicInteger allowedCount = new AtomicInteger(0);
        AtomicInteger rateLimitedCount = new AtomicInteger(0);

        for (int i = 0; i < 2; i++) {
            executor.submit(() -> {
                try {
                    barrier.await();
                    RateLimitDecision decision = policy.check(new RateLimitContext("alice"));
                    if (decision == RateLimitDecision.ALLOWED) {
                        allowedCount.incrementAndGet();
                    } else {
                        rateLimitedCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        assertTrue(doneLatch.await(10, TimeUnit.SECONDS));
        executor.shutdown();

        assertEquals(1, allowedCount.get());
        assertEquals(1, rateLimitedCount.get());
        assertEquals(100, stateStore.acceptedCount(SlidingWindowLogRateLimitPolicy.buildKey("alice")));
    }

    @Test
    void threeHundredConcurrentRequests_allowExactlyLimit() throws Exception {
        SlidingWindowLogConfig config = new SlidingWindowLogConfig(100, Duration.ofSeconds(60));
        SlidingWindowLogRateLimitPolicy policy = policy(config);

        int threadCount = 300;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger allowedCount = new AtomicInteger(0);
        AtomicInteger rateLimitedCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    barrier.await();
                    RateLimitDecision decision = policy.check(new RateLimitContext("alice"));
                    if (decision == RateLimitDecision.ALLOWED) {
                        allowedCount.incrementAndGet();
                    } else {
                        rateLimitedCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        assertTrue(doneLatch.await(10, TimeUnit.SECONDS));
        executor.shutdown();

        assertEquals(100, allowedCount.get());
        assertEquals(200, rateLimitedCount.get());
        assertEquals(100, stateStore.acceptedCount(SlidingWindowLogRateLimitPolicy.buildKey("alice")));
    }

    private SlidingWindowLogRateLimitPolicy policy(SlidingWindowLogConfig config) {
        return new SlidingWindowLogRateLimitPolicy(
                stateStore,
                config,
                () -> "member-" + memberSequence.incrementAndGet());
    }
}
