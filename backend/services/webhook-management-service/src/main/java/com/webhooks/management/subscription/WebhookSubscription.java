package com.webhooks.management.subscription;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "management", name = "webhook_subscriptions")
public class WebhookSubscription {
    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "endpoint_id", nullable = false)
    private UUID endpointId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected WebhookSubscription() {
    }

    public WebhookSubscription(UUID tenantId, UUID endpointId, String eventType) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.endpointId = endpointId;
        this.eventType = eventType;
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getEndpointId() {
        return endpointId;
    }

    public void setEndpointId(UUID endpointId) {
        this.endpointId = endpointId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void markDeleted() {
        this.active = false;
        this.deletedAt = Instant.now();
    }
}
