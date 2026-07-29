package com.webhooks.delivery.attempt;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "delivery", name = "delivery_attempts")
public class DeliveryAttempt {
    @Id
    private UUID id;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "endpoint_id", nullable = false)
    private UUID endpointId;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Column(name = "status_code")
    private Integer statusCode;

    @Column(name = "response_body")
    private String responseBody;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "attempted_at", nullable = false)
    private Instant attemptedAt;

    protected DeliveryAttempt() {
    }

    public DeliveryAttempt(
            UUID eventId,
            UUID endpointId,
            int attemptNumber,
            Integer statusCode,
            String responseBody,
            String errorMessage
    ) {
        this.id = UUID.randomUUID();
        this.eventId = eventId;
        this.endpointId = endpointId;
        this.attemptNumber = attemptNumber;
        this.statusCode = statusCode;
        this.responseBody = truncate(responseBody);
        this.errorMessage = truncate(errorMessage);
    }

    @PrePersist
    void prePersist() {
        if (attemptedAt == null) {
            attemptedAt = Instant.now();
        }
    }

    private String truncate(String value) {
        if (value == null || value.length() <= 4000) {
            return value;
        }
        return value.substring(0, 4000);
    }
}
