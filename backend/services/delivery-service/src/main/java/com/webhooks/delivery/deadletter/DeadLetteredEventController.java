package com.webhooks.delivery.deadletter;

import java.util.List;
import java.util.UUID;
import com.webhooks.delivery.job.DeliveryJob;
import com.webhooks.delivery.job.DeliveryJobPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class DeadLetteredEventController {
    private final DeadLetteredEventRepository repository;
    private final DeliveryJobPublisher deliveryJobPublisher;
    private final EndpointClient endpointClient;

    public DeadLetteredEventController(
            DeadLetteredEventRepository repository,
            DeliveryJobPublisher deliveryJobPublisher,
            EndpointClient endpointClient
    ) {
        this.repository = repository;
        this.deliveryJobPublisher = deliveryJobPublisher;
        this.endpointClient = endpointClient;
    }

    @GetMapping("/tenants/{tenantId}/dead-lettered-events")
    List<DeadLetteredEventResponse> listDeadLetteredEvents(@PathVariable UUID tenantId) {
        return repository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .map(DeadLetteredEventResponse::from)
                .toList();
    }

    @DeleteMapping("/tenants/{tenantId}/dead-lettered-events/{deadLetterId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteDeadLetteredEvent(@PathVariable UUID tenantId, @PathVariable UUID deadLetterId) {
        DeadLetteredEvent event = repository.findByIdAndTenantId(deadLetterId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Dead-lettered event not found: " + deadLetterId));
        repository.delete(event);
    }

    @PostMapping("/tenants/{tenantId}/dead-lettered-events/{deadLetterId}/replay")
    DeadLetteredEventResponse replayDeadLetteredEvent(@PathVariable UUID tenantId, @PathVariable UUID deadLetterId) {
        DeadLetteredEvent event = repository.findByIdAndTenantId(deadLetterId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Dead-lettered event not found: " + deadLetterId));
        EndpointClient.EndpointDetails endpoint = endpointClient.getEndpoint(tenantId, event.getEndpointId());
        if (!endpoint.active()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Endpoint is inactive. Activate it before replaying.");
        }

        deliveryJobPublisher.publish(new DeliveryJob(
                event.getEventId(),
                event.getTenantId(),
                event.getEndpointId(),
                event.getEventType(),
                endpoint.url(),
                endpoint.secret(),
                event.getPayload(),
                1,
                event.getCreatedAt()
        ));
        repository.delete(event);
        return DeadLetteredEventResponse.from(event);
    }
}
