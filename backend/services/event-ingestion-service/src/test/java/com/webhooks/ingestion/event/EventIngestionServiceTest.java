package com.webhooks.ingestion.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.webhooks.ingestion.common.ResourceNotFoundException;
import com.webhooks.ingestion.delivery.DeliveryJob;
import com.webhooks.ingestion.delivery.DeliveryJobPublisher;
import com.webhooks.ingestion.subscription.SubscriptionRepository;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class EventIngestionServiceTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final DeliveryJobPublisher deliveryJobPublisher = Mockito.mock(DeliveryJobPublisher.class);
    private final EventRepository eventRepository = Mockito.mock(EventRepository.class);
    private final SubscriptionRepository subscriptionRepository = Mockito.mock(SubscriptionRepository.class);
    private final TenantRepository tenantRepository = Mockito.mock(TenantRepository.class);
    private final EventIngestionService eventIngestionService = new EventIngestionService(
            deliveryJobPublisher,
            eventRepository,
            subscriptionRepository,
            tenantRepository
    );

    @BeforeEach
    void setUp() {
        Mockito.reset(deliveryJobPublisher, eventRepository, subscriptionRepository, tenantRepository);
    }

    @Test
    void ingestsEventAndPublishesDeliveryJobsForActiveSubscriptions() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID firstEndpointId = UUID.randomUUID();
        UUID secondEndpointId = UUID.randomUUID();
        JsonNode payload = payload();
        when(tenantRepository.existsById(tenantId)).thenReturn(true);
        when(eventRepository.saveAndFlush(any(Event.class))).thenAnswer(invocation -> withCreatedAt(invocation.getArgument(0)));
        when(subscriptionRepository.findActiveSubscriptions(tenantId, "order.created")).thenReturn(List.of(
                new SubscriptionView(UUID.randomUUID(), firstEndpointId, "https://first.example.com/webhooks", "first-secret"),
                new SubscriptionView(UUID.randomUUID(), secondEndpointId, "https://second.example.com/webhooks", "second-secret")
        ));

        EventIngestionResponse response = eventIngestionService.ingest(
                tenantId,
                " event-key ",
                new IngestEventRequest("order.created", payload)
        );

        assertThat(response.tenantId()).isEqualTo(tenantId);
        assertThat(response.eventType()).isEqualTo("order.created");
        assertThat(response.deliveryJobsPublished()).isEqualTo(2);
        assertThat(response.duplicate()).isFalse();
        verify(eventRepository).findByTenantIdAndIdempotencyKey(tenantId, "event-key");
        ArgumentCaptor<DeliveryJob> jobCaptor = ArgumentCaptor.forClass(DeliveryJob.class);
        verify(deliveryJobPublisher, Mockito.times(2)).publish(jobCaptor.capture());
        assertThat(jobCaptor.getAllValues())
                .extracting(DeliveryJob::endpointId)
                .containsExactly(firstEndpointId, secondEndpointId);
        assertThat(jobCaptor.getAllValues())
                .allSatisfy(job -> {
                    assertThat(job.tenantId()).isEqualTo(tenantId);
                    assertThat(job.eventType()).isEqualTo("order.created");
                    assertThat(job.payload()).isEqualTo(payload);
                    assertThat(job.attemptNumber()).isEqualTo(1);
                });
    }

    @Test
    void returnsExistingEventForDuplicateIdempotencyKey() throws Exception {
        UUID tenantId = UUID.randomUUID();
        Event existingEvent = withCreatedAt(new Event(tenantId, "order.created", payload(), "event-key"));
        when(tenantRepository.existsById(tenantId)).thenReturn(true);
        when(eventRepository.findByTenantIdAndIdempotencyKey(tenantId, "event-key")).thenReturn(Optional.of(existingEvent));

        EventIngestionResponse response = eventIngestionService.ingest(
                tenantId,
                "event-key",
                new IngestEventRequest("order.created", payload())
        );

        assertThat(response.eventId()).isEqualTo(existingEvent.getId());
        assertThat(response.deliveryJobsPublished()).isZero();
        assertThat(response.duplicate()).isTrue();
        verify(eventRepository, never()).saveAndFlush(any(Event.class));
        verify(deliveryJobPublisher, never()).publish(any(DeliveryJob.class));
    }

    @Test
    void throwsWhenTenantDoesNotExist() {
        UUID tenantId = UUID.randomUUID();
        when(tenantRepository.existsById(tenantId)).thenReturn(false);

        assertThatThrownBy(() -> eventIngestionService.ingest(
                tenantId,
                null,
                new IngestEventRequest("order.created", payload())
        )).isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Tenant not found");
    }

    @Test
    void listsTenantEvents() throws Exception {
        UUID tenantId = UUID.randomUUID();
        Event event = withCreatedAt(new Event(tenantId, "order.created", payload(), null));
        when(tenantRepository.existsById(tenantId)).thenReturn(true);
        when(eventRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)).thenReturn(List.of(event));

        List<EventResponse> responses = eventIngestionService.listEvents(tenantId);

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().id()).isEqualTo(event.getId());
    }

    @Test
    void throwsWhenEventDoesNotBelongToTenant() {
        UUID tenantId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        when(tenantRepository.existsById(tenantId)).thenReturn(true);
        when(eventRepository.findByIdAndTenantId(eventId, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> eventIngestionService.getEvent(tenantId, eventId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Event not found");
    }

    private JsonNode payload() {
        return OBJECT_MAPPER.createObjectNode().put("orderId", "ord_123");
    }

    private Event withCreatedAt(Event event) throws Exception {
        Field createdAtField = Event.class.getDeclaredField("createdAt");
        createdAtField.setAccessible(true);
        createdAtField.set(event, Instant.parse("2026-01-01T00:00:00Z"));
        return event;
    }
}
