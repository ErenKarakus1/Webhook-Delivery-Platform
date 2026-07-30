package com.webhooks.scheduler.retry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.webhooks.scheduler.delivery.DeliveryJob;
import com.webhooks.scheduler.delivery.DeliveryJobPublisher;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.web.server.ResponseStatusException;

class RetryQueueControllerTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final DeliveryJobPublisher deliveryJobPublisher = Mockito.mock(DeliveryJobPublisher.class);
    private final RetryQueueRepository retryQueueRepository = Mockito.mock(RetryQueueRepository.class);
    private final RetryQueueController controller = new RetryQueueController(deliveryJobPublisher, retryQueueRepository);

    @Test
    void dispatchesRetryAndDeletesQueueEntry() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID retryId = UUID.randomUUID();
        RetryQueueEntry entry = retryEntry(retryId, job(tenantId));
        when(retryQueueRepository.findByIdAndTenantId(retryId, tenantId)).thenReturn(Optional.of(entry));

        RetryQueueResponse response = controller.dispatchRetry(tenantId, retryId);

        assertThat(response.id()).isEqualTo(retryId);
        verify(deliveryJobPublisher).publish(entry.getPayload());
        verify(retryQueueRepository).delete(entry);
    }

    @Test
    void throwsWhenRetryDoesNotBelongToTenant() {
        UUID tenantId = UUID.randomUUID();
        UUID retryId = UUID.randomUUID();
        when(retryQueueRepository.findByIdAndTenantId(retryId, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.dispatchRetry(tenantId, retryId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Retry not found");
    }

    private RetryQueueEntry retryEntry(UUID retryId, DeliveryJob job) throws Exception {
        RetryQueueEntry entry = new RetryQueueEntry();
        setField(entry, "id", retryId);
        setField(entry, "eventId", job.eventId());
        setField(entry, "tenantId", job.tenantId());
        setField(entry, "endpointId", job.endpointId());
        setField(entry, "attemptNumber", job.attemptNumber());
        setField(entry, "dueAt", Instant.parse("2026-01-01T00:00:00Z"));
        setField(entry, "payload", job);
        return entry;
    }

    private DeliveryJob job(UUID tenantId) {
        return new DeliveryJob(
                UUID.randomUUID(),
                tenantId,
                UUID.randomUUID(),
                "order.created",
                "https://example.com/webhooks",
                "secret",
                OBJECT_MAPPER.createObjectNode().put("orderId", "ord_123"),
                2,
                Instant.parse("2026-01-01T00:00:00Z")
        );
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
