package com.webhooks.ingestion.subscription;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "management", name = "webhook_endpoints")
class EndpointRow {
    @Id
    UUID id;

    @Column(nullable = false)
    String url;

    @Column(nullable = false)
    String secret;

    @Column(name = "is_active", nullable = false)
    boolean active;

    @Column(name = "deleted_at")
    Instant deletedAt;
}
