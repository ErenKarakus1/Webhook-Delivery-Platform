package com.webhooks.delivery.job;

import com.webhooks.delivery.attempt.DeliveryAttempt;
import com.webhooks.delivery.attempt.DeliveryAttemptRepository;
import com.webhooks.delivery.http.DeliveryHttpClient;
import com.webhooks.delivery.http.DeliveryResult;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DeliveryJobConsumer {
    static final String DELIVERY_REQUESTED_TOPIC = "webhook.delivery.requested";

    private final DeliveryAttemptRepository attemptRepository;
    private final DeliveryHttpClient httpClient;

    public DeliveryJobConsumer(DeliveryAttemptRepository attemptRepository, DeliveryHttpClient httpClient) {
        this.attemptRepository = attemptRepository;
        this.httpClient = httpClient;
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
    }
}
