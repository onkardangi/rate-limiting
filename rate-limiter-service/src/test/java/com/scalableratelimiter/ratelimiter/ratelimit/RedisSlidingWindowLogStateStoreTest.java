package com.scalableratelimiter.ratelimiter.ratelimit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisSlidingWindowLogStateStoreTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @InjectMocks
    private RedisSlidingWindowLogStateStore stateStore;

    @Captor
    private ArgumentCaptor<List<String>> keysCaptor;

    @Test
    void tryAccept_executesLuaScriptOnce() {
        Duration window = Duration.ofSeconds(60);
        Duration ttl = Duration.ofSeconds(120);
        when(redisTemplate.execute(
                eq(stateStore.slidingWindowLogScript()),
                eq(List.of("rate-limit:sliding:alice")),
                eq("100"),
                eq(String.valueOf(window.toMillis())),
                eq(String.valueOf(ttl.toSeconds())),
                eq("member-1")))
                .thenReturn(1L);

        assertTrue(stateStore.tryAccept(
                "rate-limit:sliding:alice", 100, window, ttl, "member-1"));

        verify(redisTemplate).execute(
                eq(stateStore.slidingWindowLogScript()),
                keysCaptor.capture(),
                eq("100"),
                eq("60000"),
                eq("120"),
                eq("member-1"));
        assertEquals(List.of("rate-limit:sliding:alice"), keysCaptor.getValue());
    }

    @Test
    void tryAccept_returnsFalseWhenScriptReturnsZero() {
        when(redisTemplate.execute(
                org.mockito.ArgumentMatchers.any(DefaultRedisScript.class),
                org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(0L);

        assertFalse(stateStore.tryAccept(
                "rate-limit:sliding:alice",
                100,
                Duration.ofSeconds(60),
                Duration.ofSeconds(120),
                "member-1"));
    }

    @Test
    void tryAccept_wrapsRedisFailureInStateStoreException() {
        when(redisTemplate.execute(
                org.mockito.ArgumentMatchers.any(DefaultRedisScript.class),
                org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()))
                .thenThrow(new RuntimeException("connection refused"));

        assertThrows(RateLimitStateStoreException.class,
                () -> stateStore.tryAccept(
                        "rate-limit:sliding:alice",
                        100,
                        Duration.ofSeconds(60),
                        Duration.ofSeconds(120),
                        "member-1"));
    }

    @Test
    void tryAccept_wrapsNullScriptResultInStateStoreException() {
        when(redisTemplate.execute(
                org.mockito.ArgumentMatchers.any(DefaultRedisScript.class),
                org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(null);

        assertThrows(RateLimitStateStoreException.class,
                () -> stateStore.tryAccept(
                        "rate-limit:sliding:alice",
                        100,
                        Duration.ofSeconds(60),
                        Duration.ofSeconds(120),
                        "member-1"));
    }
}
