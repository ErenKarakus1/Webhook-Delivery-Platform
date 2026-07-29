package com.webhooks.management.apikey;

import com.webhooks.management.common.ResourceNotFoundException;
import com.webhooks.management.tenant.TenantService;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApiKeyService {
    private final ApiKeyGenerator apiKeyGenerator;
    private final ApiKeyHasher apiKeyHasher;
    private final ApiKeyRepository apiKeyRepository;
    private final TenantService tenantService;

    public ApiKeyService(
            ApiKeyGenerator apiKeyGenerator,
            ApiKeyHasher apiKeyHasher,
            ApiKeyRepository apiKeyRepository,
            TenantService tenantService
    ) {
        this.apiKeyGenerator = apiKeyGenerator;
        this.apiKeyHasher = apiKeyHasher;
        this.apiKeyRepository = apiKeyRepository;
        this.tenantService = tenantService;
    }

    @Transactional
    public CreateApiKeyResponse createApiKey(UUID tenantId, CreateApiKeyRequest request) {
        tenantService.getTenantEntity(tenantId);
        String plaintextKey = apiKeyGenerator.generate();
        ApiKey apiKey = apiKeyRepository.save(new ApiKey(
                tenantId,
                request.name(),
                apiKeyHasher.hash(plaintextKey),
                plaintextKey.substring(0, 8)
        ));
        return CreateApiKeyResponse.from(apiKey, plaintextKey);
    }

    @Transactional(readOnly = true)
    public List<ApiKeyResponse> listApiKeys(UUID tenantId) {
        tenantService.getTenantEntity(tenantId);
        return apiKeyRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .map(ApiKeyResponse::from)
                .toList();
    }

    @Transactional
    public void revokeApiKey(UUID tenantId, UUID apiKeyId) {
        ApiKey apiKey = apiKeyRepository.findByIdAndTenantId(apiKeyId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("API key not found: " + apiKeyId));
        apiKey.revoke();
    }
}
