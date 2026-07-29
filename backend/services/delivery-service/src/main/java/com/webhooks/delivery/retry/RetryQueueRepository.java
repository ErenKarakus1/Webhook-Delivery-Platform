package com.webhooks.delivery.retry;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RetryQueueRepository extends JpaRepository<RetryQueueEntry, UUID> {
}
