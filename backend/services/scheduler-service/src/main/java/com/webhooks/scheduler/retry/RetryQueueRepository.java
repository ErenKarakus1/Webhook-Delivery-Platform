package com.webhooks.scheduler.retry;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RetryQueueRepository extends JpaRepository<RetryQueueEntry, UUID> {
    List<RetryQueueEntry> findByTenantIdOrderByDueAtAsc(UUID tenantId);

    List<RetryQueueEntry> findByTenantIdAndEventIdOrderByDueAtAsc(UUID tenantId, UUID eventId);

    List<RetryQueueEntry> findByTenantIdAndEndpointIdOrderByDueAtAsc(UUID tenantId, UUID endpointId);

    @Query(
            value = """
                    select *
                    from scheduler.retry_queue
                    where due_at <= :now
                    order by due_at asc
                    limit :batchSize
                    for update skip locked
                    """,
            nativeQuery = true
    )
    List<RetryQueueEntry> findDueRetries(@Param("now") Instant now, @Param("batchSize") int batchSize);
}
