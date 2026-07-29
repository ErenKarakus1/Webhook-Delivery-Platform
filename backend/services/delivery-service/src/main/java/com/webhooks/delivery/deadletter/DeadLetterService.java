package com.webhooks.delivery.deadletter;

import com.webhooks.delivery.http.DeliveryResult;
import com.webhooks.delivery.job.DeliveryJob;
import org.springframework.stereotype.Service;

@Service
public class DeadLetterService {
    private final DeadLetteredEventRepository repository;

    public DeadLetterService(DeadLetteredEventRepository repository) {
        this.repository = repository;
    }

    public void record(DeliveryJob job, DeliveryResult result) {
        repository.save(new DeadLetteredEvent(job, result));
    }
}
