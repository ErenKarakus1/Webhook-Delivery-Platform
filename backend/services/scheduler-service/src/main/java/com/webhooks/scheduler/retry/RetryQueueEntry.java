package com.webhooks.scheduler.retry;

import com.webhooks.scheduler.delivery.DeliveryJob;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(schema = "scheduler", name = "retry_queue")
public class RetryQueueEntry {
    @Id
    private UUID id;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "endpoint_id", nullable = false)
    private UUID endpointId;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Column(name = "due_at", nullable = false)
    private Instant dueAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private DeliveryJob payload;

    protected RetryQueueEntry() {
    }

    public DeliveryJob getPayload() {
        return payload;
    }

    public UUID getId() {
        return id;
    }

    public UUID getEventId() {
        return eventId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getEndpointId() {
        return endpointId;
    }

    public int getAttemptNumber() {
        return attemptNumber;
    }

    public Instant getDueAt() {
        return dueAt;
    }
}
