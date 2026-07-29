package com.webhooks.scheduler.delivery;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

public record DeliveryJob(
        UUID eventId,
        UUID tenantId,
        UUID endpointId,
        String eventType,
        String url,
        String secret,
        JsonNode payload,
        int attemptNumber,
        Instant requestedAt
) {
}
