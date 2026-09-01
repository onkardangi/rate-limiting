package com.scalableratelimiter.ratelimiter.ratelimit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.List;

@Repository
@ConditionalOnProperty(name = "rate-limit.state-store", havingValue = "redis", matchIfMissing = true)
public class RedisRateLimitStateStore implements RateLimitStateStore {

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> incrementWithTtlScript;

    public RedisRateLimitStateStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.incrementWithTtlScript = createIncrementWithTtlScript();
    }

    @Override
    public long increment(String key, Duration ttl) {
        try {
            Long count = redisTemplate.execute(
                    incrementWithTtlScript,
                    List.of(key),
                    String.valueOf(ttl.toSeconds()));

            if (count == null) {
                throw new RateLimitStateStoreException(
                        "Redis increment script returned null for key: " + key);
            }

            return count;
        } catch (RateLimitStateStoreException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new RateLimitStateStoreException("Redis increment failed for key: " + key, e);
        }
    }

    DefaultRedisScript<Long> incrementWithTtlScript() {
        return incrementWithTtlScript;
    }

    private static DefaultRedisScript<Long> createIncrementWithTtlScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setResultType(Long.class);
        script.setLocation(new ClassPathResource("redis/increment-with-ttl.lua"));
        return script;
    }
}
