package com.webhooks.scheduler.retry;

import java.time.Instant;
import java.util.UUID;

public record RetryQueueResponse(
        UUID id,
        UUID eventId,
        UUID endpointId,
        int attemptNumber,
        Instant dueAt
) {
    static RetryQueueResponse from(RetryQueueEntry entry) {
        return new RetryQueueResponse(
                entry.getId(),
                entry.getEventId(),
                entry.getEndpointId(),
                entry.getAttemptNumber(),
                entry.getDueAt()
        );
    }
}
