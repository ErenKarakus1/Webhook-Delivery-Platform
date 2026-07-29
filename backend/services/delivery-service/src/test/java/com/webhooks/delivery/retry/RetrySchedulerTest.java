package com.webhooks.delivery.retry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.webhooks.delivery.job.DeliveryJob;
import java.lang.reflect.Field;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class RetrySchedulerTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final RetryQueueRepository retryQueueRepository = Mockito.mock(RetryQueueRepository.class);

    @Test
    void schedulesNextAttemptWithBackoff() throws Exception {
        RetryScheduler retryScheduler = new RetryScheduler(6, retryQueueRepository);
        DeliveryJob job = job(2);
        Instant before = Instant.now().plus(Duration.ofMinutes(5)).minusSeconds(2);

        retryScheduler.scheduleIfNeeded(job);

        RetryQueueEntry entry = savedRetry();
        DeliveryJob retryJob = field(entry, "payload", DeliveryJob.class);
        Instant dueAt = field(entry, "dueAt", Instant.class);
        assertThat(retryJob.attemptNumber()).isEqualTo(3);
        assertThat(retryJob.eventId()).isEqualTo(job.eventId());
        assertThat(retryJob.tenantId()).isEqualTo(job.tenantId());
        assertThat(retryJob.endpointId()).isEqualTo(job.endpointId());
        assertThat(dueAt).isAfterOrEqualTo(before);
        assertThat(dueAt).isBeforeOrEqualTo(Instant.now().plus(Duration.ofMinutes(5)).plusSeconds(2));
    }

    @Test
    void doesNotScheduleWhenAttemptsAreExhausted() {
        RetryScheduler retryScheduler = new RetryScheduler(3, retryQueueRepository);

        retryScheduler.scheduleIfNeeded(job(3));

        verify(retryQueueRepository, never()).save(Mockito.any(RetryQueueEntry.class));
    }

    @Test
    void reportsExhaustedAtMaxAttempts() {
        RetryScheduler retryScheduler = new RetryScheduler(3, retryQueueRepository);

        assertThat(retryScheduler.isExhausted(job(2))).isFalse();
        assertThat(retryScheduler.isExhausted(job(3))).isTrue();
    }

    private RetryQueueEntry savedRetry() {
        ArgumentCaptor<RetryQueueEntry> retryCaptor = ArgumentCaptor.forClass(RetryQueueEntry.class);
        verify(retryQueueRepository).save(retryCaptor.capture());
        return retryCaptor.getValue();
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

    private <T> T field(Object target, String fieldName, Class<T> type) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return type.cast(field.get(target));
    }
}
