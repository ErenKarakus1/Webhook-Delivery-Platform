package com.webhooks.scheduler.retry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.webhooks.scheduler.delivery.DeliveryJob;
import com.webhooks.scheduler.delivery.DeliveryJobPublisher;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class RetryDispatcherTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final DeliveryJobPublisher deliveryJobPublisher = Mockito.mock(DeliveryJobPublisher.class);
    private final RetryQueueRepository retryQueueRepository = Mockito.mock(RetryQueueRepository.class);
    private final RetryDispatcher retryDispatcher = new RetryDispatcher(25, deliveryJobPublisher, retryQueueRepository);

    @BeforeEach
    void setUp() {
        Mockito.reset(deliveryJobPublisher, retryQueueRepository);
    }

    @Test
    void dispatchesDueRetriesAndDeletesEntries() throws Exception {
        RetryQueueEntry firstEntry = retryEntry(job(2));
        RetryQueueEntry secondEntry = retryEntry(job(3));
        when(retryQueueRepository.findDueRetries(any(Instant.class), Mockito.eq(25))).thenReturn(List.of(firstEntry, secondEntry));

        retryDispatcher.dispatchDueRetries();

        ArgumentCaptor<DeliveryJob> jobCaptor = ArgumentCaptor.forClass(DeliveryJob.class);
        verify(deliveryJobPublisher, Mockito.times(2)).publish(jobCaptor.capture());
        assertThat(jobCaptor.getAllValues()).containsExactly(firstEntry.getPayload(), secondEntry.getPayload());
        verify(retryQueueRepository).delete(firstEntry);
        verify(retryQueueRepository).delete(secondEntry);
    }

    @Test
    void doesNothingWhenNoRetriesAreDue() {
        when(retryQueueRepository.findDueRetries(any(Instant.class), Mockito.eq(25))).thenReturn(List.of());

        retryDispatcher.dispatchDueRetries();

        verify(deliveryJobPublisher, never()).publish(any(DeliveryJob.class));
        verify(retryQueueRepository, never()).delete(any(RetryQueueEntry.class));
    }

    @Test
    void queriesRepositoryWithConfiguredBatchSize() {
        when(retryQueueRepository.findDueRetries(any(Instant.class), Mockito.eq(25))).thenReturn(List.of());

        retryDispatcher.dispatchDueRetries();

        verify(retryQueueRepository).findDueRetries(any(Instant.class), Mockito.eq(25));
    }

    private RetryQueueEntry retryEntry(DeliveryJob job) throws Exception {
        RetryQueueEntry entry = new RetryQueueEntry();
        setField(entry, "id", UUID.randomUUID());
        setField(entry, "eventId", job.eventId());
        setField(entry, "tenantId", job.tenantId());
        setField(entry, "endpointId", job.endpointId());
        setField(entry, "attemptNumber", job.attemptNumber());
        setField(entry, "dueAt", Instant.parse("2026-01-01T00:00:00Z"));
        setField(entry, "payload", job);
        return entry;
    }

    private DeliveryJob job(int attemptNumber) {
        return new DeliveryJob(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "order.created",
                "https://example.com/webhooks",
                "secret",
                OBJECT_MAPPER.createObjectNode().put("orderId", "ord_123"),
                attemptNumber,
                Instant.parse("2026-01-01T00:00:00Z")
        );
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
