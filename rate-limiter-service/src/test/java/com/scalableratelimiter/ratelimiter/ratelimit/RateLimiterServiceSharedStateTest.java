package com.scalableratelimiter.ratelimiter.ratelimit;

import com.scalableratelimiter.ratelimiter.ratelimit.policy.FixedWindowRateLimitPolicy;
import com.scalableratelimiter.ratelimiter.ratelimit.policy.config.FixedWindowConfig;
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
        RateLimiterService serviceA = RateLimiterServiceTestSupport.service(clock, stateStore);
        RateLimiterService serviceB = RateLimiterServiceTestSupport.service(clock, stateStore);

        for (int i = 0; i < FixedWindowConfig.DEFAULT_LIMIT; i++) {
            assertEquals(RateLimitDecision.ALLOWED, serviceA.checkRequest("alice"));
        }

        assertEquals(RateLimitDecision.RATE_LIMITED, serviceB.checkRequest("alice"));
    }

    @Test
    void firstIncrement_returnsOne() {
        String key = FixedWindowRateLimitPolicy.buildKey("alice", 123L);

        assertEquals(1, stateStore.increment(key, FixedWindowConfig.defaults().keyTtl()));
    }

    @Test
    void subsequentIncrements_returnIncreasingCounts() {
        String key = FixedWindowRateLimitPolicy.buildKey("alice", 123L);
        Duration ttl = FixedWindowConfig.defaults().keyTtl();

        assertEquals(1, stateStore.increment(key, ttl));
        assertEquals(2, stateStore.increment(key, ttl));
        assertEquals(3, stateStore.increment(key, ttl));
    }

    @Test
    void firstIncrement_configuresWindowKeyExpiration() {
        String key = FixedWindowRateLimitPolicy.buildKey("alice", 123L);
        stateStore.increment(key, FixedWindowConfig.defaults().keyTtl());

        Duration remainingTtl = stateStore.ttlFor(key);
        assertTrue(remainingTtl.compareTo(Duration.ofMinutes(1)) > 0);
        assertTrue(remainingTtl.compareTo(FixedWindowConfig.defaults().keyTtl()) <= 0);
    }

    @Test
    void subsequentIncrements_doNotResetCounterToOne() {
        String key = FixedWindowRateLimitPolicy.buildKey("alice", 123L);
        Duration ttl = FixedWindowConfig.defaults().keyTtl();

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
        RateLimiterService service = RateLimiterServiceTestSupport.service(clock, stateStore);

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
                        if (service.checkRequest("alice") == RateLimitDecision.ALLOWED) {
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

        assertEquals(FixedWindowConfig.DEFAULT_LIMIT, allowedCount.get());
    }

    @Test
    void concurrentRequestsInNewWindow_resetCorrectly() {
        RateLimiterService service = RateLimiterServiceTestSupport.service(clock, stateStore);

        for (int i = 0; i < FixedWindowConfig.DEFAULT_LIMIT; i++) {
            assertEquals(RateLimitDecision.ALLOWED, service.checkRequest("alice"));
        }
        assertEquals(RateLimitDecision.RATE_LIMITED, service.checkRequest("alice"));

        clock = Clock.fixed(MINUTE_N_PLUS_ONE, ZoneOffset.UTC);
        stateStore = new InMemoryRateLimitStateStore(clock);
        service = RateLimiterServiceTestSupport.service(clock, stateStore);

        assertEquals(RateLimitDecision.ALLOWED, service.checkRequest("alice"));
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
