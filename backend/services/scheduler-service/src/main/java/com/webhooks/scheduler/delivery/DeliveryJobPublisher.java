package com.webhooks.scheduler.delivery;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class DeliveryJobPublisher {
    static final String DELIVERY_REQUESTED_TOPIC = "webhook.delivery.requested";

    private final KafkaTemplate<String, DeliveryJob> kafkaTemplate;

    public DeliveryJobPublisher(KafkaTemplate<String, DeliveryJob> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(DeliveryJob job) {
        kafkaTemplate.send(DELIVERY_REQUESTED_TOPIC, job.eventId().toString(), job);
    }
}
