package com.webhooks.management.endpoint;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EndpointRepository extends JpaRepository<WebhookEndpoint, UUID> {
    List<WebhookEndpoint> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    Optional<WebhookEndpoint> findByIdAndTenantId(UUID id, UUID tenantId);
}
