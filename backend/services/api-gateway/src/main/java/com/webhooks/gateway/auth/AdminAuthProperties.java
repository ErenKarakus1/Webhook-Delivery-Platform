package com.webhooks.gateway.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gateway.admin")
public record AdminAuthProperties(
        String apiKey
) {
}
