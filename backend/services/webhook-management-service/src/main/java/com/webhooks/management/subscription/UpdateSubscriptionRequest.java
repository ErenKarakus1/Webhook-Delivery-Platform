package com.webhooks.management.subscription;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record UpdateSubscriptionRequest(
        @NotNull UUID endpointId,
        @NotBlank @Size(max = 160) String eventType
) {
}
