package com.webhooks.gateway.ratelimit;

public record RateLimitResult(
        boolean allowed,
        long limit,
        long remaining
) {
}
