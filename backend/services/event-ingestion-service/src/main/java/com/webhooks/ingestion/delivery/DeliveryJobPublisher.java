package com.webhooks.ingestion.delivery;

import java.util.UUID;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class DeliveryJobPublisher {
    static final String DELIVERY_REQUESTED_TOPIC = "webhook.delivery.requested";

    private final KafkaTemplate<String, DeliveryJob> kafkaTemplate;

    public DeliveryJobPublisher(KafkaTemplate<String, DeliveryJob> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(DeliveryJob deliveryJob) {
        UUID eventId = deliveryJob.eventId();
        kafkaTemplate.send(DELIVERY_REQUESTED_TOPIC, eventId.toString(), deliveryJob);
    }
}
