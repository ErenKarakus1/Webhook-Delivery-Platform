package com.webhooks.delivery.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.webhooks.delivery.job.DeliveryJob;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

class DeliveryHttpClientTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void sendsWebhookSignatureHeaders() {
        AtomicReference<ClientRequest> requestReference = new AtomicReference<>();
        ExchangeFunction exchangeFunction = request -> {
            requestReference.set(request);
            return Mono.just(ClientResponse.create(HttpStatus.OK).body("ok").build());
        };
        WebhookSignatureService signatureService = Mockito.mock(WebhookSignatureService.class);
        when(signatureService.sign("secret", "timestamp", "{\"orderId\":\"ord_123\"}")).thenReturn("sha256=test-signature");
        DeliveryHttpClient client = new DeliveryHttpClient(
                WebClient.builder().exchangeFunction(exchangeFunction),
                Duration.ofSeconds(2),
                signatureService,
                () -> "timestamp"
        );

        DeliveryResult result = client.deliver(job());

        ClientRequest request = requestReference.get();
        assertThat(result.successful()).isTrue();
        assertThat(request.url().toString()).isEqualTo("https://example.com/webhooks");
        assertThat(request.headers().getFirst("User-Agent")).isEqualTo("WebhookDeliveryPlatform/0.1");
        assertThat(request.headers().getFirst("X-Webhook-Event")).isEqualTo("order.created");
        assertThat(request.headers().getFirst("X-Webhook-Event-Id")).isEqualTo("11111111-1111-1111-1111-111111111111");
        assertThat(request.headers().getFirst("X-Webhook-Timestamp")).isEqualTo("timestamp");
        assertThat(request.headers().getFirst("X-Webhook-Signature")).isEqualTo("sha256=test-signature");
    }

    private DeliveryJob job() {
        JsonNode payload = OBJECT_MAPPER.createObjectNode().put("orderId", "ord_123");
        return new DeliveryJob(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "order.created",
                "https://example.com/webhooks",
                "secret",
                payload,
                1,
                Instant.parse("2026-01-01T00:00:00Z")
        );
    }
}
