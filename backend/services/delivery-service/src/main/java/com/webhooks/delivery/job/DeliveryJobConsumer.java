package com.webhooks.delivery.job;

import com.webhooks.delivery.attempt.DeliveryAttempt;
import com.webhooks.delivery.attempt.DeliveryAttemptRepository;
import com.webhooks.delivery.http.DeliveryHttpClient;
import com.webhooks.delivery.http.DeliveryResult;
import com.webhooks.delivery.retry.RetryScheduler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DeliveryJobConsumer {
    static final String DELIVERY_REQUESTED_TOPIC = "webhook.delivery.requested";

    private final DeliveryAttemptRepository attemptRepository;
    private final DeliveryHttpClient httpClient;
    private final RetryScheduler retryScheduler;

    public DeliveryJobConsumer(
            DeliveryAttemptRepository attemptRepository,
            DeliveryHttpClient httpClient,
            RetryScheduler retryScheduler
    ) {
        this.attemptRepository = attemptRepository;
        this.httpClient = httpClient;
        this.retryScheduler = retryScheduler;
    }

    @KafkaListener(topics = DELIVERY_REQUESTED_TOPIC)
    @Transactional
    public void consume(DeliveryJob job) {
        DeliveryResult result = httpClient.deliver(job);
        attemptRepository.save(new DeliveryAttempt(
                job.eventId(),
                job.endpointId(),
                job.attemptNumber(),
                result.statusCode(),
                result.responseBody(),
                result.errorMessage()
        ));
        if (!result.successful()) {
            retryScheduler.scheduleIfNeeded(job);
        }
    }
}
