package com.webhooks.gateway.ratelimit;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RateLimiter {
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final RateLimitProperties properties;
    private final StringRedisTemplate redisTemplate;

    public RateLimiter(RateLimitProperties properties, StringRedisTemplate redisTemplate) {
        this.properties = properties;
        this.redisTemplate = redisTemplate;
    }

    public RateLimitResult check(UUID tenantId) {
        long window = Instant.now().getEpochSecond() / WINDOW.toSeconds();
        String key = "rate-limit:tenant:" + tenantId + ":" + window;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, WINDOW.plusSeconds(5));
        }

        long limit = properties.requestsPerMinute();
        long used = count == null ? limit : count;
        return new RateLimitResult(used <= limit, limit, Math.max(0, limit - used));
    }
}
