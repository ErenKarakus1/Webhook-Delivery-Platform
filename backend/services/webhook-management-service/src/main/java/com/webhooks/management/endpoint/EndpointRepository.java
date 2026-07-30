package com.webhooks.management.endpoint;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EndpointRepository extends JpaRepository<WebhookEndpoint, UUID> {
    List<WebhookEndpoint> findByTenantIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID tenantId);

    Optional<WebhookEndpoint> findByIdAndTenantIdAndDeletedAtIsNull(UUID id, UUID tenantId);

    boolean existsByTenantIdAndUrlIgnoreCaseAndActiveTrueAndDeletedAtIsNull(UUID tenantId, String url);

    boolean existsByTenantIdAndUrlIgnoreCaseAndActiveTrueAndDeletedAtIsNullAndIdNot(UUID tenantId, String url, UUID id);
}
