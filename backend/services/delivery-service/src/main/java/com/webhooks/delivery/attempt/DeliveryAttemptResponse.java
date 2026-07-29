package com.webhooks.delivery.attempt;

import java.time.Instant;
import java.util.UUID;

public record DeliveryAttemptResponse(
        UUID id,
        UUID eventId,
        UUID tenantId,
        UUID endpointId,
        int attemptNumber,
        Integer statusCode,
        String responseBody,
        String errorMessage,
        Instant attemptedAt
) {
    static DeliveryAttemptResponse from(DeliveryAttempt attempt) {
        return new DeliveryAttemptResponse(
                attempt.getId(),
                attempt.getEventId(),
                attempt.getTenantId(),
                attempt.getEndpointId(),
                attempt.getAttemptNumber(),
                attempt.getStatusCode(),
                attempt.getResponseBody(),
                attempt.getErrorMessage(),
                attempt.getAttemptedAt()
        );
    }
}
