package com.webhooks.ingestion.event;

import java.util.UUID;

public record SubscriptionView(
        UUID subscriptionId,
        UUID endpointId,
        String url,
        String secret
) {
}
