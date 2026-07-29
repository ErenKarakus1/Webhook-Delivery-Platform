package com.webhooks.management.apikey;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/tenants/{tenantId}/api-keys")
public class ApiKeyController {
    private final ApiKeyService apiKeyService;

    public ApiKeyController(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @PostMapping
    ResponseEntity<CreateApiKeyResponse> createApiKey(
            @PathVariable UUID tenantId,
            @Valid @RequestBody CreateApiKeyRequest request
    ) {
        CreateApiKeyResponse response = apiKeyService.createApiKey(tenantId, request);
        return ResponseEntity.created(URI.create("/tenants/" + tenantId + "/api-keys/" + response.id()))
                .body(response);
    }

    @GetMapping
    List<ApiKeyResponse> listApiKeys(@PathVariable UUID tenantId) {
        return apiKeyService.listApiKeys(tenantId);
    }

    @DeleteMapping("/{apiKeyId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revokeApiKey(@PathVariable UUID tenantId, @PathVariable UUID apiKeyId) {
        apiKeyService.revokeApiKey(tenantId, apiKeyId);
    }
}
