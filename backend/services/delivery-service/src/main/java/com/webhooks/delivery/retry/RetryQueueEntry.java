package com.webhooks.delivery.retry;

import com.webhooks.delivery.job.DeliveryJob;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
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

    @Column(name = "endpoint_id", nullable = false)
    private UUID endpointId;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Column(name = "due_at", nullable = false)
    private Instant dueAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private DeliveryJob payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected RetryQueueEntry() {
    }

    public RetryQueueEntry(DeliveryJob payload, Instant dueAt) {
        this.id = UUID.randomUUID();
        this.eventId = payload.eventId();
        this.endpointId = payload.endpointId();
        this.attemptNumber = payload.attemptNumber();
        this.dueAt = dueAt;
        this.payload = payload;
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
