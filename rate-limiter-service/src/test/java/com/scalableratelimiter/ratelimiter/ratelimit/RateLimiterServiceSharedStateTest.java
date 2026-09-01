package com.scalableratelimiter.ratelimiter.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimiterServiceSharedStateTest {

    private static final Instant MINUTE_N_START = Instant.parse("2026-08-31T12:00:00Z");
    private static final Instant MINUTE_N_PLUS_ONE = Instant.parse("2026-08-31T12:01:00Z");

    private Clock clock;
    private InMemoryRateLimitStateStore stateStore;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(MINUTE_N_START, ZoneOffset.UTC);
        stateStore = new InMemoryRateLimitStateStore(clock);
    }

    @Test
    void twoServiceInstances_shareSameStateStore() {
        RateLimiterService serviceA = new RateLimiterService(clock, stateStore);
        RateLimiterService serviceB = new RateLimiterService(clock, stateStore);

        for (int i = 0; i < RateLimiterService.MAX_REQUESTS_PER_MINUTE; i++) {
            assertTrue(serviceA.allowRequest("alice"));
        }

        assertFalse(serviceB.allowRequest("alice"));
    }

    @Test
    void firstIncrement_returnsOne() {
        String key = RateLimiterService.buildKey("alice", 123L);

        assertEquals(1, stateStore.increment(key, RateLimiterService.WINDOW_KEY_TTL));
    }

    @Test
    void subsequentIncrements_returnIncreasingCounts() {
        String key = RateLimiterService.buildKey("alice", 123L);
        Duration ttl = RateLimiterService.WINDOW_KEY_TTL;

        assertEquals(1, stateStore.increment(key, ttl));
        assertEquals(2, stateStore.increment(key, ttl));
        assertEquals(3, stateStore.increment(key, ttl));
    }

    @Test
    void firstIncrement_configuresWindowKeyExpiration() {
        String key = RateLimiterService.buildKey("alice", 123L);
        stateStore.increment(key, RateLimiterService.WINDOW_KEY_TTL);

        Duration remainingTtl = stateStore.ttlFor(key);
        assertTrue(remainingTtl.compareTo(Duration.ofMinutes(1)) > 0);
        assertTrue(remainingTtl.compareTo(RateLimiterService.WINDOW_KEY_TTL) <= 0);
    }

    @Test
    void subsequentIncrements_doNotResetCounterToOne() {
        String key = RateLimiterService.buildKey("alice", 123L);
        Duration ttl = RateLimiterService.WINDOW_KEY_TTL;

        stateStore.increment(key, ttl);
        stateStore.increment(key, ttl);

        assertEquals(3, stateStore.increment(key, ttl));
    }

    @Test
    void expiredWindowKeys_startNewCounter() {
        MutableClock mutableClock = new MutableClock(MINUTE_N_START, ZoneOffset.UTC);
        InMemoryRateLimitStateStore expiringStore = new InMemoryRateLimitStateStore(mutableClock);
        String key = "rate-limit:alice:1";

        assertEquals(1, expiringStore.increment(key, Duration.ofSeconds(1)));
        mutableClock.advance(Duration.ofSeconds(2));

        assertEquals(1, expiringStore.increment(key, Duration.ofSeconds(30)));
    }

    @Test
    void concurrentIncrements_allowExactlyOneHundred() throws Exception {
        RateLimiterService service = new RateLimiterService(clock, stateStore);

        int threadCount = 50;
        int requestsPerThread = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger allowedCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    barrier.await();
                    for (int request = 0; request < requestsPerThread; request++) {
                        if (service.allowRequest("alice")) {
                            allowedCount.incrementAndGet();
                        }
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        assertTrue(doneLatch.await(10, TimeUnit.SECONDS), "Concurrent requests did not finish in time");
        executor.shutdown();

        assertEquals(RateLimiterService.MAX_REQUESTS_PER_MINUTE, allowedCount.get());
    }

    @Test
    void concurrentRequestsInNewWindow_resetCorrectly() {
        RateLimiterService service = new RateLimiterService(clock, stateStore);

        for (int i = 0; i < RateLimiterService.MAX_REQUESTS_PER_MINUTE; i++) {
            assertTrue(service.allowRequest("alice"));
        }
        assertFalse(service.allowRequest("alice"));

        clock = Clock.fixed(MINUTE_N_PLUS_ONE, ZoneOffset.UTC);
        stateStore = new InMemoryRateLimitStateStore(clock);
        service = new RateLimiterService(clock, stateStore);

        assertTrue(service.allowRequest("alice"));
    }

    private static final class MutableClock extends Clock {

        private Instant instant;
        private final ZoneId zone;

        private MutableClock(Instant instant, ZoneId zone) {
            this.instant = instant;
            this.zone = zone;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new MutableClock(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
