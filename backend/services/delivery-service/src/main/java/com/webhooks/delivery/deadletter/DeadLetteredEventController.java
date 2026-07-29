package com.webhooks.delivery.deadletter;

import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

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
}
