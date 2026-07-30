package com.webhooks.delivery.deadletter;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

@Component
public class EndpointClient {
    private final String managementBaseUrl;
    private final WebClient webClient;

    public EndpointClient(
            @Value("${services.webhook-management.base-url:http://localhost:8083}") String managementBaseUrl,
            WebClient.Builder webClientBuilder
    ) {
        this.managementBaseUrl = managementBaseUrl;
        this.webClient = webClientBuilder.build();
    }

    Mono<EndpointDetails> getEndpoint(UUID tenantId, UUID endpointId) {
        return webClient.get()
                .uri(managementBaseUrl + "/tenants/{tenantId}/endpoints/{endpointId}", tenantId, endpointId)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> Mono.error(new ResponseStatusException(
                        response.statusCode(),
                        "Endpoint not available for replay: " + endpointId
                )))
                .bodyToMono(EndpointDetails.class);
    }

    record EndpointDetails(
            UUID id,
            UUID tenantId,
            String url,
            String secret,
            boolean active
    ) {
    }
}
