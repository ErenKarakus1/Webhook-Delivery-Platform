package com.webhooks.management.endpoint;

import java.time.Instant;
import java.util.UUID;

public record EndpointResponse(
        UUID id,
        UUID tenantId,
        String url,
        String secret,
        boolean active,
        Instant createdAt
) {
    static EndpointResponse from(WebhookEndpoint endpoint) {
        return new EndpointResponse(
                endpoint.getId(),
                endpoint.getTenantId(),
                endpoint.getUrl(),
                endpoint.getSecret(),
                endpoint.isActive(),
                endpoint.getCreatedAt()
        );
    }
}
