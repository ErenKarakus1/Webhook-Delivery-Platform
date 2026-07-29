package com.webhooks.management.endpoint;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

public record CreateEndpointRequest(
        @NotBlank @URL(protocol = "https") @Size(max = 2048) String url
) {
}
