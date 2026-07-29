package com.webhooks.scheduler.retry;

import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RetryQueueController {
    private final RetryQueueRepository retryQueueRepository;

    public RetryQueueController(RetryQueueRepository retryQueueRepository) {
        this.retryQueueRepository = retryQueueRepository;
    }

    @GetMapping("/tenants/{tenantId}/retries")
    List<RetryQueueResponse> listRetries(
            @PathVariable UUID tenantId,
            @RequestParam(required = false) UUID eventId,
            @RequestParam(required = false) UUID endpointId
    ) {
        if (eventId != null) {
            return retryQueueRepository.findByTenantIdAndEventIdOrderByDueAtAsc(tenantId, eventId).stream()
                    .map(RetryQueueResponse::from)
                    .toList();
        }
        if (endpointId != null) {
            return retryQueueRepository.findByTenantIdAndEndpointIdOrderByDueAtAsc(tenantId, endpointId).stream()
                    .map(RetryQueueResponse::from)
                    .toList();
        }
        return retryQueueRepository.findByTenantIdOrderByDueAtAsc(tenantId).stream()
                .map(RetryQueueResponse::from)
                .toList();
    }
}
