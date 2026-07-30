package com.webhooks.scheduler.retry;

import com.webhooks.scheduler.delivery.DeliveryJobPublisher;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

@RestController
public class RetryQueueController {
    private final DeliveryJobPublisher deliveryJobPublisher;
    private final RetryQueueRepository retryQueueRepository;

    public RetryQueueController(DeliveryJobPublisher deliveryJobPublisher, RetryQueueRepository retryQueueRepository) {
        this.deliveryJobPublisher = deliveryJobPublisher;
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

    @PostMapping("/tenants/{tenantId}/retries/{retryId}/dispatch")
    @Transactional
    RetryQueueResponse dispatchRetry(@PathVariable UUID tenantId, @PathVariable UUID retryId) {
        RetryQueueEntry entry = retryQueueRepository.findByIdAndTenantId(retryId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Retry not found: " + retryId));
        deliveryJobPublisher.publish(entry.getPayload());
        retryQueueRepository.delete(entry);
        return RetryQueueResponse.from(entry);
    }
}
