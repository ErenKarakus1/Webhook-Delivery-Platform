package com.webhooks.delivery.http;

import com.webhooks.delivery.job.DeliveryJob;
import java.time.Duration;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class DeliveryHttpClient {
    private final WebClient webClient;
    private final Duration timeout;
    private final WebhookSignatureService signatureService;
    private final Supplier<String> timestampSupplier;

    @Autowired
    public DeliveryHttpClient(
            WebClient.Builder webClientBuilder,
            @Value("${webhook.delivery.timeout}") Duration timeout,
            WebhookSignatureService signatureService
    ) {
        this(
                webClientBuilder,
                timeout,
                signatureService,
                () -> Long.toString(System.currentTimeMillis() / 1000)
        );
    }

    DeliveryHttpClient(
            WebClient.Builder webClientBuilder,
            Duration timeout,
            WebhookSignatureService signatureService,
            Supplier<String> timestampSupplier
    ) {
        this.webClient = webClientBuilder.build();
        this.timeout = timeout;
        this.signatureService = signatureService;
        this.timestampSupplier = timestampSupplier;
    }

    public DeliveryResult deliver(DeliveryJob job) {
        String payload = job.payload().toString();
        String timestamp = timestampSupplier.get();
        String signature = signatureService.sign(job.secret(), timestamp, payload);

        try {
            return webClient.post()
                    .uri(job.url())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("User-Agent", "WebhookDeliveryPlatform/0.1")
                    .header("X-Webhook-Event", job.eventType())
                    .header("X-Webhook-Event-Id", job.eventId().toString())
                    .header("X-Webhook-Timestamp", timestamp)
                    .header("X-Webhook-Signature", signature)
                    .bodyValue(payload)
                    .exchangeToMono(response -> response.bodyToMono(String.class)
                            .defaultIfEmpty("")
                            .map(body -> DeliveryResult.http(response.statusCode().value(), body)))
                    .timeout(timeout)
                    .block();
        } catch (RuntimeException exception) {
            return DeliveryResult.failure(exception.getMessage());
        }
    }
}
