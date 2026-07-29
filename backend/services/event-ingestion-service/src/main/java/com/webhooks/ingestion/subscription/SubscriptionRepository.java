package com.webhooks.ingestion.subscription;

import com.webhooks.ingestion.event.SubscriptionView;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface SubscriptionRepository extends Repository<SubscriptionRow, UUID> {
    @Query("""
            select new com.webhooks.ingestion.event.SubscriptionView(
                subscription.id,
                endpoint.id,
                endpoint.url,
                endpoint.secret
            )
            from SubscriptionRow subscription
            join EndpointRow endpoint on endpoint.id = subscription.endpointId
            where subscription.tenantId = :tenantId
              and subscription.eventType = :eventType
              and subscription.active = true
              and endpoint.active = true
            """)
    List<SubscriptionView> findActiveSubscriptions(
            @Param("tenantId") UUID tenantId,
            @Param("eventType") String eventType
    );
}
