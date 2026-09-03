package com.scalableratelimiter.ratelimiter.ratelimit.policy;

import com.scalableratelimiter.ratelimiter.ratelimit.InMemorySlidingWindowLogStateStore;
import com.scalableratelimiter.ratelimiter.ratelimit.MutableSlidingWindowTimeSource;
import com.scalableratelimiter.ratelimiter.ratelimit.RateLimitDecision;
import com.scalableratelimiter.ratelimiter.ratelimit.policy.config.SlidingWindowLogConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlidingWindowLogRateLimitPolicyTest {

    private static final long BASE_TIME_MS = 1_700_000_000_000L;

    private MutableSlidingWindowTimeSource timeSource;
    private InMemorySlidingWindowLogStateStore stateStore;
    private AtomicInteger memberSequence;
    private SlidingWindowLogRateLimitPolicy policy;
    private SlidingWindowLogConfig config;

    @BeforeEach
    void setUp() {
        timeSource = new MutableSlidingWindowTimeSource(BASE_TIME_MS);
        stateStore = new InMemorySlidingWindowLogStateStore(timeSource);
        memberSequence = new AtomicInteger();
        config = new SlidingWindowLogConfig(100, Duration.ofSeconds(60));
        policy = new SlidingWindowLogRateLimitPolicy(
                stateStore,
                config,
                () -> "member-" + memberSequence.incrementAndGet());
    }

    @Test
    void firstRequest_isAllowed() {
        assertEquals(RateLimitDecision.ALLOWED, policy.check(new RateLimitContext("alice")));
    }

    @Test
    void requestsOneThroughLimit_areAllowed() {
        for (int i = 0; i < config.limit(); i++) {
            assertEquals(RateLimitDecision.ALLOWED, policy.check(new RateLimitContext("alice")),
                    "Request " + (i + 1) + " should be allowed");
        }
    }

    @Test
    void requestBeyondLimit_isRateLimited() {
        for (int i = 0; i < config.limit(); i++) {
            policy.check(new RateLimitContext("alice"));
        }

        assertEquals(RateLimitDecision.RATE_LIMITED, policy.check(new RateLimitContext("alice")));
    }

    @Test
    void differentUsers_areIndependent() {
        for (int i = 0; i < config.limit(); i++) {
            policy.check(new RateLimitContext("alice"));
        }

        assertEquals(RateLimitDecision.RATE_LIMITED, policy.check(new RateLimitContext("alice")));
        assertEquals(RateLimitDecision.ALLOWED, policy.check(new RateLimitContext("bob")));
    }

    @Test
    void oldAcceptedRequestsFallOutOfRollingWindow() {
        SlidingWindowLogConfig smallWindow = new SlidingWindowLogConfig(1, Duration.ofSeconds(10));
        SlidingWindowLogRateLimitPolicy smallWindowPolicy = new SlidingWindowLogRateLimitPolicy(
                stateStore,
                smallWindow,
                () -> "member-" + memberSequence.incrementAndGet());

        assertEquals(RateLimitDecision.ALLOWED, smallWindowPolicy.check(new RateLimitContext("alice")));
        assertEquals(RateLimitDecision.RATE_LIMITED, smallWindowPolicy.check(new RateLimitContext("alice")));

        timeSource.advance(Duration.ofSeconds(11));

        assertEquals(RateLimitDecision.ALLOWED, smallWindowPolicy.check(new RateLimitContext("alice")));
    }

    @Test
    void requestExactlyWindowDurationOld_doesNotCount() {
        SlidingWindowLogConfig windowConfig = new SlidingWindowLogConfig(1, Duration.ofMillis(10_000));
        SlidingWindowLogRateLimitPolicy windowPolicy = new SlidingWindowLogRateLimitPolicy(
                stateStore,
                windowConfig,
                () -> "member-" + memberSequence.incrementAndGet());

        timeSource.setNowEpochMillis(BASE_TIME_MS);
        assertEquals(RateLimitDecision.ALLOWED, windowPolicy.check(new RateLimitContext("alice")));

        timeSource.setNowEpochMillis(BASE_TIME_MS + 10_000);
        assertEquals(RateLimitDecision.ALLOWED, windowPolicy.check(new RateLimitContext("alice")));
    }

    @Test
    void requestJustInsideRollingWindowStillCounts() {
        SlidingWindowLogConfig windowConfig = new SlidingWindowLogConfig(1, Duration.ofMillis(10_000));
        SlidingWindowLogRateLimitPolicy windowPolicy = new SlidingWindowLogRateLimitPolicy(
                stateStore,
                windowConfig,
                () -> "member-" + memberSequence.incrementAndGet());

        timeSource.setNowEpochMillis(BASE_TIME_MS);
        assertEquals(RateLimitDecision.ALLOWED, windowPolicy.check(new RateLimitContext("alice")));

        timeSource.setNowEpochMillis(BASE_TIME_MS + 9_999);
        assertEquals(RateLimitDecision.RATE_LIMITED, windowPolicy.check(new RateLimitContext("alice")));
    }

    @Test
    void sameMillisecondRequests_areCountedSeparately() {
        SlidingWindowLogConfig windowConfig = new SlidingWindowLogConfig(2, Duration.ofSeconds(60));
        SlidingWindowLogRateLimitPolicy windowPolicy = new SlidingWindowLogRateLimitPolicy(
                stateStore,
                windowConfig,
                () -> "member-" + memberSequence.incrementAndGet());

        assertEquals(RateLimitDecision.ALLOWED, windowPolicy.check(new RateLimitContext("alice")));
        assertEquals(RateLimitDecision.ALLOWED, windowPolicy.check(new RateLimitContext("alice")));
        assertEquals(RateLimitDecision.RATE_LIMITED, windowPolicy.check(new RateLimitContext("alice")));
        assertEquals(2, stateStore.acceptedCount(SlidingWindowLogRateLimitPolicy.buildKey("alice")));
    }

    @Test
    void rejectedRequestsAreNotStored() {
        for (int i = 0; i < config.limit(); i++) {
            policy.check(new RateLimitContext("alice"));
        }

        int countBeforeRejection = stateStore.acceptedCount(SlidingWindowLogRateLimitPolicy.buildKey("alice"));
        assertEquals(config.limit(), countBeforeRejection);

        policy.check(new RateLimitContext("alice"));

        assertEquals(countBeforeRejection,
                stateStore.acceptedCount(SlidingWindowLogRateLimitPolicy.buildKey("alice")));
    }

    @Test
    void inactiveKeyCleanupTtl_isSafelyBeyondWindow() {
        policy.check(new RateLimitContext("alice"));

        Duration remainingTtl = stateStore.ttlFor(SlidingWindowLogRateLimitPolicy.buildKey("alice"));
        assertTrue(remainingTtl.compareTo(config.windowDuration()) > 0);
        assertTrue(remainingTtl.compareTo(config.keyTtl()) <= 0);
    }

    @Test
    void rejectedTrafficDoesNotRefreshCleanupTtl() {
        for (int i = 0; i < config.limit(); i++) {
            policy.check(new RateLimitContext("alice"));
        }

        Duration ttlAfterLimit = stateStore.ttlFor(SlidingWindowLogRateLimitPolicy.buildKey("alice"));
        policy.check(new RateLimitContext("alice"));
        Duration ttlAfterRejection = stateStore.ttlFor(SlidingWindowLogRateLimitPolicy.buildKey("alice"));

        assertEquals(ttlAfterLimit, ttlAfterRejection);
    }

    @Test
    void buildKey_usesSlidingPrefix() {
        assertEquals("rate-limit:sliding:alice", SlidingWindowLogRateLimitPolicy.buildKey("alice"));
    }
}
