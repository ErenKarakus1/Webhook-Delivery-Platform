package com.webhooks.delivery.deadletter;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class DeadLetteredEventController {
    private final DeadLetteredEventRepository repository;

    public DeadLetteredEventController(DeadLetteredEventRepository repository) {
        this.repository = repository;
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
}
