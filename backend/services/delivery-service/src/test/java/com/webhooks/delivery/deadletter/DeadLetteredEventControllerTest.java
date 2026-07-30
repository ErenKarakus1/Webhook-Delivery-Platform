package com.webhooks.delivery.deadletter;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.webhooks.delivery.http.DeliveryResult;
import com.webhooks.delivery.job.DeliveryJob;
import com.webhooks.delivery.job.DeliveryJobPublisher;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

class DeadLetteredEventControllerTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final DeadLetteredEventRepository repository = Mockito.mock(DeadLetteredEventRepository.class);
    private final DeliveryJobPublisher deliveryJobPublisher = Mockito.mock(DeliveryJobPublisher.class);
    private final EndpointClient endpointClient = Mockito.mock(EndpointClient.class);
    private final DeadLetteredEventController controller = new DeadLetteredEventController(
            repository,
            deliveryJobPublisher,
            endpointClient
    );

    @Test
    void deletesTenantScopedDeadLetteredEvent() {
        UUID tenantId = UUID.randomUUID();
        UUID deadLetterId = UUID.randomUUID();
        DeadLetteredEvent event = deadLetteredEvent(tenantId);
        when(repository.findByIdAndTenantId(deadLetterId, tenantId)).thenReturn(Optional.of(event));

        controller.deleteDeadLetteredEvent(tenantId, deadLetterId);

        verify(repository).delete(event);
    }

    @Test
    void throwsWhenDeadLetteredEventDoesNotBelongToTenant() {
        UUID tenantId = UUID.randomUUID();
        UUID deadLetterId = UUID.randomUUID();
        when(repository.findByIdAndTenantId(deadLetterId, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.deleteDeadLetteredEvent(tenantId, deadLetterId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Dead-lettered event not found");
    }

    @Test
    void replaysDeadLetteredEventAndDeletesIt() {
        UUID tenantId = UUID.randomUUID();
        UUID deadLetterId = UUID.randomUUID();
        DeadLetteredEvent event = deadLetteredEvent(tenantId);
        when(repository.findByIdAndTenantId(deadLetterId, tenantId)).thenReturn(Optional.of(event));
        when(endpointClient.getEndpoint(tenantId, event.getEndpointId())).thenReturn(Mono.just(new EndpointClient.EndpointDetails(
                event.getEndpointId(),
                tenantId,
                "https://example.com/webhooks",
                "secret",
                true
        )));

        DeadLetteredEventResponse response = controller.replayDeadLetteredEvent(tenantId, deadLetterId).block();

        ArgumentCaptor<DeliveryJob> jobCaptor = ArgumentCaptor.forClass(DeliveryJob.class);
        verify(deliveryJobPublisher).publish(jobCaptor.capture());
        DeliveryJob replayedJob = jobCaptor.getValue();
        assertThat(replayedJob.eventId()).isEqualTo(event.getEventId());
        assertThat(replayedJob.endpointId()).isEqualTo(event.getEndpointId());
        assertThat(replayedJob.attemptNumber()).isEqualTo(1);
        assertThat(response.id()).isEqualTo(event.getId());
        verify(repository).delete(event);
    }

    @Test
    void rejectsReplayWhenEndpointIsInactive() {
        UUID tenantId = UUID.randomUUID();
        UUID deadLetterId = UUID.randomUUID();
        DeadLetteredEvent event = deadLetteredEvent(tenantId);
        when(repository.findByIdAndTenantId(deadLetterId, tenantId)).thenReturn(Optional.of(event));
        when(endpointClient.getEndpoint(tenantId, event.getEndpointId())).thenReturn(Mono.just(new EndpointClient.EndpointDetails(
                event.getEndpointId(),
                tenantId,
                "https://example.com/webhooks",
                "secret",
                false
        )));

        assertThatThrownBy(() -> controller.replayDeadLetteredEvent(tenantId, deadLetterId).block())
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Endpoint is inactive");
    }

    private DeadLetteredEvent deadLetteredEvent(UUID tenantId) {
        DeliveryJob job = new DeliveryJob(
                UUID.randomUUID(),
                tenantId,
                UUID.randomUUID(),
                "order.created",
                "https://example.com/webhooks",
                "secret",
                OBJECT_MAPPER.createObjectNode().put("orderId", "ord_123"),
                3,
                Instant.parse("2026-01-01T00:00:00Z")
        );
        return new DeadLetteredEvent(job, DeliveryResult.http(500, "failed"));
    }
}
