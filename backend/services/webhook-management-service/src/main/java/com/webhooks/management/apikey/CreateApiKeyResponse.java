package com.webhooks.management.apikey;

import java.time.Instant;
import java.util.UUID;

public record CreateApiKeyResponse(
        UUID id,
        UUID tenantId,
        String name,
        String keyPrefix,
        String apiKey,
        Instant createdAt
) {
    static CreateApiKeyResponse from(ApiKey apiKey, String plaintextKey) {
        return new CreateApiKeyResponse(
                apiKey.getId(),
                apiKey.getTenantId(),
                apiKey.getName(),
                apiKey.getKeyPrefix(),
                plaintextKey,
                apiKey.getCreatedAt()
        );
    }
}
