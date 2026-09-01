package com.scalableratelimiter.ratelimiter.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimiterServiceConcurrencyTest {

    private static final Instant MINUTE_N_START = Instant.parse("2026-08-31T12:00:00Z");
    private static final Instant MINUTE_N_PLUS_ONE = Instant.parse("2026-08-31T12:01:00Z");

    private Clock clock;
    private RateLimiterService rateLimiterService;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(MINUTE_N_START, ZoneOffset.UTC);
        rateLimiterService = new RateLimiterService(clock);
    }

    @Test
    void concurrentRequestsForSameUser_allowExactlyOneHundred() throws Exception {
        int requestCount = 300;
        ExecutorService executor = Executors.newFixedThreadPool(32);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(requestCount);
        AtomicInteger allowedCount = new AtomicInteger(0);

        for (int i = 0; i < requestCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    if (rateLimiterService.allowRequest("alice")) {
                        allowedCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS), "Concurrent requests did not finish in time");
        executor.shutdown();

        assertEquals(RateLimiterService.MAX_REQUESTS_PER_MINUTE, allowedCount.get());
    }

    @Test
    void concurrentRequestsForDifferentUsers_maintainIndependentLimits() throws Exception {
        int requestCountPerUser = 200;
        String[] users = {"alice", "bob"};
        ExecutorService executor = Executors.newFixedThreadPool(32);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(requestCountPerUser * users.length);
        AtomicInteger aliceAllowed = new AtomicInteger(0);
        AtomicInteger bobAllowed = new AtomicInteger(0);

        for (String user : users) {
            for (int i = 0; i < requestCountPerUser; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        if (rateLimiterService.allowRequest(user)) {
                            if ("alice".equals(user)) {
                                aliceAllowed.incrementAndGet();
                            } else {
                                bobAllowed.incrementAndGet();
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS), "Concurrent requests did not finish in time");
        executor.shutdown();

        assertEquals(RateLimiterService.MAX_REQUESTS_PER_MINUTE, aliceAllowed.get());
        assertEquals(RateLimiterService.MAX_REQUESTS_PER_MINUTE, bobAllowed.get());
    }

    @Test
    void concurrentRequestsInNewWindow_resetCorrectly() throws Exception {
        for (int i = 0; i < RateLimiterService.MAX_REQUESTS_PER_MINUTE; i++) {
            assertTrue(rateLimiterService.allowRequest("alice"));
        }
        assertFalse(rateLimiterService.allowRequest("alice"));

        clock = Clock.fixed(MINUTE_N_PLUS_ONE, ZoneOffset.UTC);
        rateLimiterService = new RateLimiterService(clock);

        int requestCount = 250;
        ExecutorService executor = Executors.newFixedThreadPool(32);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(requestCount);
        AtomicInteger allowedCount = new AtomicInteger(0);

        for (int i = 0; i < requestCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    if (rateLimiterService.allowRequest("alice")) {
                        allowedCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS), "Concurrent requests did not finish in time");
        executor.shutdown();

        assertEquals(RateLimiterService.MAX_REQUESTS_PER_MINUTE, allowedCount.get());
    }
}
