package com.webhooks.ingestion.event;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/tenants/{tenantId}/events")
public class EventController {
    private final EventIngestionService eventIngestionService;

    public EventController(EventIngestionService eventIngestionService) {
        this.eventIngestionService = eventIngestionService;
    }

    @PostMapping
    ResponseEntity<EventIngestionResponse> ingestEvent(
            @PathVariable UUID tenantId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody IngestEventRequest request
    ) {
        EventIngestionResponse response = eventIngestionService.ingest(tenantId, idempotencyKey, request);
        if (response.duplicate()) {
            return ResponseEntity.ok(response);
        }
        return ResponseEntity.created(URI.create("/tenants/" + tenantId + "/events/" + response.eventId()))
                .body(response);
    }
}
