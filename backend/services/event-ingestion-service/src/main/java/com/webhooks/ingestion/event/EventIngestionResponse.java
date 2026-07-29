package com.webhooks.ingestion.event;

import java.time.Instant;
import java.util.UUID;

public record EventIngestionResponse(
        UUID eventId,
        UUID tenantId,
        String eventType,
        int deliveryJobsPublished,
        boolean duplicate,
        Instant createdAt
) {
}
