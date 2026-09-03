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
public class RedisSlidingWindowLogStateStore implements SlidingWindowLogStateStore {

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> slidingWindowLogScript;

    public RedisSlidingWindowLogStateStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.slidingWindowLogScript = createSlidingWindowLogScript();
    }

    @Override
    public boolean tryAccept(String key,
                             int limit,
                             Duration windowDuration,
                             Duration keyTtl,
                             String uniqueMember) {
        try {
            Long result = redisTemplate.execute(
                    slidingWindowLogScript,
                    List.of(key),
                    String.valueOf(limit),
                    String.valueOf(windowDuration.toMillis()),
                    String.valueOf(keyTtl.toSeconds()),
                    uniqueMember);

            if (result == null) {
                throw new RateLimitStateStoreException(
                        "Redis sliding-window script returned null for key: " + key);
            }

            return result == 1L;
        } catch (RateLimitStateStoreException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new RateLimitStateStoreException(
                    "Redis sliding-window accept failed for key: " + key, e);
        }
    }

    DefaultRedisScript<Long> slidingWindowLogScript() {
        return slidingWindowLogScript;
    }

    private static DefaultRedisScript<Long> createSlidingWindowLogScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setResultType(Long.class);
        script.setLocation(new ClassPathResource("redis/sliding-window-log.lua"));
        return script;
    }
}
