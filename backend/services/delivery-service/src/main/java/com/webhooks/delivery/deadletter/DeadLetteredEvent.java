package com.webhooks.delivery.deadletter;

import com.fasterxml.jackson.databind.JsonNode;
import com.webhooks.delivery.http.DeliveryResult;
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
@Table(schema = "delivery", name = "dead_lettered_events")
public class DeadLetteredEvent {
    @Id
    private UUID id;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "endpoint_id", nullable = false)
    private UUID endpointId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Column(name = "status_code")
    private Integer statusCode;

    @Column(name = "error_message")
    private String errorMessage;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private JsonNode payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected DeadLetteredEvent() {
    }

    public DeadLetteredEvent(DeliveryJob job, DeliveryResult result) {
        this.id = UUID.randomUUID();
        this.eventId = job.eventId();
        this.tenantId = job.tenantId();
        this.endpointId = job.endpointId();
        this.eventType = job.eventType();
        this.attemptNumber = job.attemptNumber();
        this.statusCode = result.statusCode();
        this.errorMessage = truncate(result.errorMessage());
        this.payload = job.payload();
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    private String truncate(String value) {
        if (value == null || value.length() <= 4000) {
            return value;
        }
        return value.substring(0, 4000);
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

    public String getEventType() {
        return eventType;
    }

    public int getAttemptNumber() {
        return attemptNumber;
    }

    public Integer getStatusCode() {
        return statusCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public JsonNode getPayload() {
        return payload;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
