package com.webhooks.delivery.attempt;

import java.util.UUID;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryAttemptRepository extends JpaRepository<DeliveryAttempt, UUID> {
    List<DeliveryAttempt> findByTenantIdOrderByAttemptedAtDesc(UUID tenantId);

    List<DeliveryAttempt> findByTenantIdAndEventIdOrderByAttemptNumberAsc(UUID tenantId, UUID eventId);

    List<DeliveryAttempt> findByTenantIdAndEndpointIdOrderByAttemptedAtDesc(UUID tenantId, UUID endpointId);
}
