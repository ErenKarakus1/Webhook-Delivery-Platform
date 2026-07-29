package com.webhooks.scheduler.retry;

import com.webhooks.scheduler.delivery.DeliveryJobPublisher;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class RetryDispatcher {
    private final int batchSize;
    private final DeliveryJobPublisher deliveryJobPublisher;
    private final RetryQueueRepository retryQueueRepository;

    public RetryDispatcher(
            @Value("${webhook.scheduler.batch-size}") int batchSize,
            DeliveryJobPublisher deliveryJobPublisher,
            RetryQueueRepository retryQueueRepository
    ) {
        this.batchSize = batchSize;
        this.deliveryJobPublisher = deliveryJobPublisher;
        this.retryQueueRepository = retryQueueRepository;
    }

    @Scheduled(fixedDelayString = "${webhook.scheduler.fixed-delay}")
    @Transactional
    public void dispatchDueRetries() {
        List<RetryQueueEntry> entries = retryQueueRepository.findDueRetries(Instant.now(), batchSize);
        entries.forEach(entry -> {
            deliveryJobPublisher.publish(entry.getPayload());
            retryQueueRepository.delete(entry);
        });
    }
}
