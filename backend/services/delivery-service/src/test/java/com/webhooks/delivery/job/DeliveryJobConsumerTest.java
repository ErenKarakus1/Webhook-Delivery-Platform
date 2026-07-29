package com.webhooks.delivery.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.webhooks.delivery.attempt.DeliveryAttempt;
import com.webhooks.delivery.attempt.DeliveryAttemptRepository;
import com.webhooks.delivery.deadletter.DeadLetterService;
import com.webhooks.delivery.http.DeliveryHttpClient;
import com.webhooks.delivery.http.DeliveryResult;
import com.webhooks.delivery.retry.RetryScheduler;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class DeliveryJobConsumerTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final DeliveryAttemptRepository attemptRepository = Mockito.mock(DeliveryAttemptRepository.class);
    private final DeadLetterService deadLetterService = Mockito.mock(DeadLetterService.class);
    private final DeliveryHttpClient httpClient = Mockito.mock(DeliveryHttpClient.class);
    private final RetryScheduler retryScheduler = Mockito.mock(RetryScheduler.class);
    private final DeliveryJobConsumer consumer = new DeliveryJobConsumer(
            attemptRepository,
            deadLetterService,
            httpClient,
            retryScheduler
    );

    @BeforeEach
    void setUp() {
        Mockito.reset(attemptRepository, deadLetterService, httpClient, retryScheduler);
    }

    @Test
    void recordsSuccessfulAttemptWithoutRetryOrDeadLetter() {
        DeliveryJob job = job(1);
        when(httpClient.deliver(job)).thenReturn(DeliveryResult.http(204, "accepted"));

        consumer.consume(job);

        DeliveryAttempt attempt = savedAttempt();
        assertThat(attempt.getEventId()).isEqualTo(job.eventId());
        assertThat(attempt.getTenantId()).isEqualTo(job.tenantId());
        assertThat(attempt.getEndpointId()).isEqualTo(job.endpointId());
        assertThat(attempt.getAttemptNumber()).isEqualTo(1);
        assertThat(attempt.getStatusCode()).isEqualTo(204);
        assertThat(attempt.getResponseBody()).isEqualTo("accepted");
        verify(retryScheduler, never()).scheduleIfNeeded(any(DeliveryJob.class));
        verify(deadLetterService, never()).record(any(DeliveryJob.class), any(DeliveryResult.class));
    }

    @Test
    void schedulesRetryForFailedNonExhaustedAttempt() {
        DeliveryJob job = job(2);
        DeliveryResult result = DeliveryResult.http(500, "server error");
        when(httpClient.deliver(job)).thenReturn(result);
        when(retryScheduler.isExhausted(job)).thenReturn(false);

        consumer.consume(job);

        DeliveryAttempt attempt = savedAttempt();
        assertThat(attempt.getAttemptNumber()).isEqualTo(2);
        assertThat(attempt.getStatusCode()).isEqualTo(500);
        verify(retryScheduler).scheduleIfNeeded(job);
        verify(deadLetterService, never()).record(any(DeliveryJob.class), any(DeliveryResult.class));
    }

    @Test
    void deadLettersFailedExhaustedAttempt() {
        DeliveryJob job = job(6);
        DeliveryResult result = DeliveryResult.failure("timeout");
        when(httpClient.deliver(job)).thenReturn(result);
        when(retryScheduler.isExhausted(job)).thenReturn(true);

        consumer.consume(job);

        DeliveryAttempt attempt = savedAttempt();
        assertThat(attempt.getAttemptNumber()).isEqualTo(6);
        assertThat(attempt.getErrorMessage()).isEqualTo("timeout");
        verify(deadLetterService).record(job, result);
        verify(retryScheduler, never()).scheduleIfNeeded(any(DeliveryJob.class));
    }

    private DeliveryAttempt savedAttempt() {
        ArgumentCaptor<DeliveryAttempt> attemptCaptor = ArgumentCaptor.forClass(DeliveryAttempt.class);
        verify(attemptRepository).save(attemptCaptor.capture());
        return attemptCaptor.getValue();
    }

    private DeliveryJob job(int attemptNumber) {
        JsonNode payload = OBJECT_MAPPER.createObjectNode().put("orderId", "ord_123");
        return new DeliveryJob(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "order.created",
                "https://example.com/webhooks",
                "secret",
                payload,
                attemptNumber,
                Instant.parse("2026-01-01T00:00:00Z")
        );
    }
}
