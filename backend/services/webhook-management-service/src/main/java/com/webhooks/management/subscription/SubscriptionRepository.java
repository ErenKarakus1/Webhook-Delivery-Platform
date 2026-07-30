package com.webhooks.management.subscription;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionRepository extends JpaRepository<WebhookSubscription, UUID> {
    List<WebhookSubscription> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    Optional<WebhookSubscription> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByEndpointIdAndEventTypeIgnoreCase(UUID endpointId, String eventType);

    boolean existsByEndpointIdAndEventTypeIgnoreCaseAndIdNot(UUID endpointId, String eventType, UUID id);
}
