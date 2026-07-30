package com.webhooks.management.subscription;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubscriptionRepository extends JpaRepository<WebhookSubscription, UUID> {
    List<WebhookSubscription> findByTenantIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID tenantId);

    Optional<WebhookSubscription> findByIdAndTenantIdAndDeletedAtIsNull(UUID id, UUID tenantId);

    boolean existsByEndpointIdAndEventTypeIgnoreCaseAndDeletedAtIsNull(UUID endpointId, String eventType);

    boolean existsByEndpointIdAndEventTypeIgnoreCaseAndDeletedAtIsNullAndIdNot(UUID endpointId, String eventType, UUID id);

    @Modifying
    @Query("""
            update WebhookSubscription subscription
            set subscription.active = false,
                subscription.deletedAt = CURRENT_TIMESTAMP
            where subscription.tenantId = :tenantId
              and subscription.endpointId = :endpointId
              and subscription.deletedAt is null
            """)
    int softDeleteByTenantIdAndEndpointId(@Param("tenantId") UUID tenantId, @Param("endpointId") UUID endpointId);
}
