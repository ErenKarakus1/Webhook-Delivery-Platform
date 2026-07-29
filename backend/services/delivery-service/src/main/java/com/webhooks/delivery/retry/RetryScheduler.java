package com.webhooks.delivery.retry;

import com.webhooks.delivery.job.DeliveryJob;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RetryScheduler {
    private static final List<Duration> BACKOFFS = List.of(
            Duration.ofMinutes(1),
            Duration.ofMinutes(5),
            Duration.ofMinutes(15),
            Duration.ofHours(1),
            Duration.ofHours(6),
            Duration.ofHours(24)
    );

    private final int maxAttempts;
    private final RetryQueueRepository retryQueueRepository;

    public RetryScheduler(
            @Value("${webhook.delivery.max-attempts}") int maxAttempts,
            RetryQueueRepository retryQueueRepository
    ) {
        this.maxAttempts = maxAttempts;
        this.retryQueueRepository = retryQueueRepository;
    }

    public void scheduleIfNeeded(DeliveryJob job) {
        int nextAttempt = job.attemptNumber() + 1;
        if (nextAttempt > maxAttempts) {
            return;
        }

        Duration backoff = BACKOFFS.get(Math.min(job.attemptNumber() - 1, BACKOFFS.size() - 1));
        DeliveryJob retryJob = new DeliveryJob(
                job.eventId(),
                job.tenantId(),
                job.endpointId(),
                job.eventType(),
                job.url(),
                job.secret(),
                job.payload(),
                nextAttempt,
                Instant.now()
        );
        retryQueueRepository.save(new RetryQueueEntry(retryJob, Instant.now().plus(backoff)));
    }
}
