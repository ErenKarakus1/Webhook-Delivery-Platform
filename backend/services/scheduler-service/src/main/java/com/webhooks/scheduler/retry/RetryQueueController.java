package com.webhooks.scheduler.retry;

import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RetryQueueController {
    private final RetryQueueRepository retryQueueRepository;

    public RetryQueueController(RetryQueueRepository retryQueueRepository) {
        this.retryQueueRepository = retryQueueRepository;
    }

    @GetMapping("/retries")
    List<RetryQueueResponse> listRetries(
            @RequestParam(required = false) UUID eventId,
            @RequestParam(required = false) UUID endpointId
    ) {
        if (eventId != null) {
            return retryQueueRepository.findByEventIdOrderByDueAtAsc(eventId).stream()
                    .map(RetryQueueResponse::from)
                    .toList();
        }
        if (endpointId != null) {
            return retryQueueRepository.findByEndpointIdOrderByDueAtAsc(endpointId).stream()
                    .map(RetryQueueResponse::from)
                    .toList();
        }
        return retryQueueRepository.findAll().stream()
                .map(RetryQueueResponse::from)
                .toList();
    }
}
