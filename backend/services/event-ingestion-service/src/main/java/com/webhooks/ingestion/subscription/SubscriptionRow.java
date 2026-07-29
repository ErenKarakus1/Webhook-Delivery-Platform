package com.webhooks.ingestion.subscription;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(schema = "management", name = "webhook_subscriptions")
class SubscriptionRow {
    @Id
    UUID id;

    @Column(name = "tenant_id", nullable = false)
    UUID tenantId;

    @Column(name = "endpoint_id", nullable = false)
    UUID endpointId;

    @Column(name = "event_type", nullable = false)
    String eventType;

    @Column(name = "is_active", nullable = false)
    boolean active;
}
