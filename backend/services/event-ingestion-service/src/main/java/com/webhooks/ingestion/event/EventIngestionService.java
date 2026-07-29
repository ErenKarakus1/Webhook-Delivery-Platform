package com.webhooks.ingestion.event;

import com.webhooks.ingestion.common.ResourceNotFoundException;
import com.webhooks.ingestion.delivery.DeliveryJob;
import com.webhooks.ingestion.delivery.DeliveryJobPublisher;
import com.webhooks.ingestion.subscription.SubscriptionRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventIngestionService {
    private final DeliveryJobPublisher deliveryJobPublisher;
    private final EventRepository eventRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final TenantRepository tenantRepository;

    public EventIngestionService(
            DeliveryJobPublisher deliveryJobPublisher,
            EventRepository eventRepository,
            SubscriptionRepository subscriptionRepository,
            TenantRepository tenantRepository
    ) {
        this.deliveryJobPublisher = deliveryJobPublisher;
        this.eventRepository = eventRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.tenantRepository = tenantRepository;
    }

    @Transactional
    public EventIngestionResponse ingest(UUID tenantId, String idempotencyKey, IngestEventRequest request) {
        ensureTenantExists(tenantId);

        String normalizedIdempotencyKey = normalizeIdempotencyKey(idempotencyKey);
        if (normalizedIdempotencyKey != null) {
            return eventRepository.findByTenantIdAndIdempotencyKey(tenantId, normalizedIdempotencyKey)
                    .map(existing -> toResponse(existing, 0, true))
                    .orElseGet(() -> ingestNewEvent(tenantId, normalizedIdempotencyKey, request));
        }

        return ingestNewEvent(tenantId, null, request);
    }

    @Transactional(readOnly = true)
    public List<EventResponse> listEvents(UUID tenantId) {
        ensureTenantExists(tenantId);
        return eventRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .map(EventResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public EventResponse getEvent(UUID tenantId, UUID eventId) {
        ensureTenantExists(tenantId);
        return eventRepository.findByIdAndTenantId(eventId, tenantId)
                .map(EventResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + eventId));
    }

    private EventIngestionResponse ingestNewEvent(UUID tenantId, String idempotencyKey, IngestEventRequest request) {
        Event event = insertEvent(tenantId, idempotencyKey, request);
        List<SubscriptionView> subscriptions = subscriptionRepository.findActiveSubscriptions(
                tenantId,
                request.eventType()
        );

        subscriptions.forEach(subscription -> deliveryJobPublisher.publish(new DeliveryJob(
                event.getId(),
                tenantId,
                subscription.endpointId(),
                event.getEventType(),
                subscription.url(),
                subscription.secret(),
                event.getPayload(),
                1,
                Instant.now()
        )));

        return toResponse(event, subscriptions.size(), false);
    }

    private void ensureTenantExists(UUID tenantId) {
        if (!tenantRepository.existsById(tenantId)) {
            throw new ResourceNotFoundException("Tenant not found: " + tenantId);
        }
    }

    private Event insertEvent(UUID tenantId, String idempotencyKey, IngestEventRequest request) {
        try {
            return eventRepository.saveAndFlush(new Event(tenantId, request.eventType(), request.payload(), idempotencyKey));
        } catch (DataIntegrityViolationException exception) {
            if (idempotencyKey == null) {
                throw exception;
            }
            return eventRepository.findByTenantIdAndIdempotencyKey(tenantId, idempotencyKey)
                    .orElseThrow(() -> exception);
        }
    }

    private String normalizeIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return null;
        }
        return idempotencyKey.trim();
    }

    private EventIngestionResponse toResponse(Event event, int deliveryJobsPublished, boolean duplicate) {
        return new EventIngestionResponse(
                event.getId(),
                event.getTenantId(),
                event.getEventType(),
                deliveryJobsPublished,
                duplicate,
                event.getCreatedAt()
        );
    }
}
