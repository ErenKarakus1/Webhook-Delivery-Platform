package com.webhooks.management.apikey;

import java.time.Instant;
import java.util.UUID;

public record ApiKeyResponse(
        UUID id,
        UUID tenantId,
        String name,
        String keyPrefix,
        Instant createdAt,
        Instant revokedAt
) {
    static ApiKeyResponse from(ApiKey apiKey) {
        return new ApiKeyResponse(
                apiKey.getId(),
                apiKey.getTenantId(),
                apiKey.getName(),
                apiKey.getKeyPrefix(),
                apiKey.getCreatedAt(),
                apiKey.getRevokedAt()
        );
    }
}
