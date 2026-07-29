package com.webhooks.gateway.proxy;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "services")
public record GatewayRoutesProperties(
        ServiceRoute webhookManagement,
        ServiceRoute eventIngestion,
        ServiceRoute delivery,
        ServiceRoute scheduler
) {
    public record ServiceRoute(String baseUrl) {
    }
}
