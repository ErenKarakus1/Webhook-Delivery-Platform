package com.webhooks.delivery.deadletter;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeadLetteredEventRepository extends JpaRepository<DeadLetteredEvent, UUID> {
    List<DeadLetteredEvent> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
