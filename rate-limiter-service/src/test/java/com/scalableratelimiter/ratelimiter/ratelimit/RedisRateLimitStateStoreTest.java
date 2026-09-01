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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisRateLimitStateStoreTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @InjectMocks
    private RedisRateLimitStateStore stateStore;

    @Captor
    private ArgumentCaptor<List<String>> keysCaptor;

    @Test
    void increment_executesLuaScriptOnce() {
        Duration ttl = Duration.ofMinutes(2);
        when(redisTemplate.execute(
                eq(stateStore.incrementWithTtlScript()),
                eq(List.of("rate-limit:alice:123")),
                eq(String.valueOf(ttl.toSeconds()))))
                .thenReturn(3L);

        assertEquals(3, stateStore.increment("rate-limit:alice:123", ttl));

        verify(redisTemplate).execute(
                eq(stateStore.incrementWithTtlScript()),
                keysCaptor.capture(),
                eq(String.valueOf(ttl.toSeconds())));
        assertEquals(List.of("rate-limit:alice:123"), keysCaptor.getValue());
        verify(redisTemplate, never()).expire(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(Duration.class));
    }

    @Test
    void increment_passesSuppliedTtlToScript() {
        Duration ttl = Duration.ofMinutes(2);
        when(redisTemplate.execute(
                org.mockito.ArgumentMatchers.any(DefaultRedisScript.class),
                org.mockito.ArgumentMatchers.anyList(),
                eq(String.valueOf(ttl.toSeconds()))))
                .thenReturn(1L);

        assertEquals(1, stateStore.increment("rate-limit:alice:123", ttl));

        verify(redisTemplate).execute(
                eq(stateStore.incrementWithTtlScript()),
                eq(List.of("rate-limit:alice:123")),
                eq("120"));
        verify(redisTemplate, never()).expire(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(Duration.class));
    }

    @Test
    void increment_returnsCountFromScriptResult() {
        when(redisTemplate.execute(
                org.mockito.ArgumentMatchers.any(DefaultRedisScript.class),
                org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(2L);

        assertEquals(2, stateStore.increment("rate-limit:alice:123", Duration.ofMinutes(2)));
    }

    @Test
    void increment_doesNotCallExpireDirectlyFromJava() {
        when(redisTemplate.execute(
                org.mockito.ArgumentMatchers.any(DefaultRedisScript.class),
                org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(1L);

        stateStore.increment("rate-limit:alice:123", Duration.ofMinutes(2));

        verify(redisTemplate, never()).expire(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(Duration.class));
        verify(redisTemplate, never()).opsForValue();
    }
}
