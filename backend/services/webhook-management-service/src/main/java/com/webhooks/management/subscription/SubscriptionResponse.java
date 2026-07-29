package com.webhooks.management.subscription;

import java.time.Instant;
import java.util.UUID;

public record SubscriptionResponse(
        UUID id,
        UUID tenantId,
        UUID endpointId,
        String eventType,
        boolean active,
        Instant createdAt
) {
    static SubscriptionResponse from(WebhookSubscription subscription) {
        return new SubscriptionResponse(
                subscription.getId(),
                subscription.getTenantId(),
                subscription.getEndpointId(),
                subscription.getEventType(),
                subscription.isActive(),
                subscription.getCreatedAt()
        );
    }
}
