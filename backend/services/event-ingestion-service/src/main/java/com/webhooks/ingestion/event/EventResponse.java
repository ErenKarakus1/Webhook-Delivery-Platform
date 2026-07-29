package com.webhooks.ingestion.event;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

public record EventResponse(
        UUID id,
        UUID tenantId,
        String eventType,
        JsonNode payload,
        String idempotencyKey,
        Instant createdAt
) {
    static EventResponse from(Event event) {
        return new EventResponse(
                event.getId(),
                event.getTenantId(),
                event.getEventType(),
                event.getPayload(),
                event.getIdempotencyKey(),
                event.getCreatedAt()
        );
    }
}
