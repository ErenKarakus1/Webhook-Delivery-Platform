package com.webhooks.delivery.attempt;

import java.util.UUID;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryAttemptRepository extends JpaRepository<DeliveryAttempt, UUID> {
    List<DeliveryAttempt> findByEventIdOrderByAttemptNumberAsc(UUID eventId);

    List<DeliveryAttempt> findByEndpointIdOrderByAttemptedAtDesc(UUID endpointId);
}
