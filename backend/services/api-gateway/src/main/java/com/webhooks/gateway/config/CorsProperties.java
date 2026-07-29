package com.webhooks.gateway.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gateway.cors")
public record CorsProperties(
        List<String> allowedOrigins
) {
}
