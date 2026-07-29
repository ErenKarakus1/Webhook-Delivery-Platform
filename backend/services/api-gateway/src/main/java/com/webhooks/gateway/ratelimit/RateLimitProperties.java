package com.webhooks.gateway.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gateway.rate-limit")
public record RateLimitProperties(
        int requestsPerMinute
) {
}
