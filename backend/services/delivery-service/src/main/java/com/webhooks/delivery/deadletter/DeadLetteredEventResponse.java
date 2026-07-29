package com.webhooks.delivery.deadletter;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

public record DeadLetteredEventResponse(
        UUID id,
        UUID eventId,
        UUID tenantId,
        UUID endpointId,
        String eventType,
        int attemptNumber,
        Integer statusCode,
        String errorMessage,
        JsonNode payload,
        Instant createdAt
) {
    static DeadLetteredEventResponse from(DeadLetteredEvent event) {
        return new DeadLetteredEventResponse(
                event.getId(),
                event.getEventId(),
                event.getTenantId(),
                event.getEndpointId(),
                event.getEventType(),
                event.getAttemptNumber(),
                event.getStatusCode(),
                event.getErrorMessage(),
                event.getPayload(),
                event.getCreatedAt()
        );
    }
}
