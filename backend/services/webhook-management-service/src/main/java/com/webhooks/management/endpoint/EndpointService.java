package com.webhooks.management.endpoint;

import com.webhooks.management.common.ResourceNotFoundException;
import com.webhooks.management.tenant.TenantService;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EndpointService {
    private final EndpointRepository endpointRepository;
    private final EndpointSecretGenerator secretGenerator;
    private final EndpointUrlValidator urlValidator;
    private final TenantService tenantService;

    public EndpointService(
            EndpointRepository endpointRepository,
            EndpointSecretGenerator secretGenerator,
            EndpointUrlValidator urlValidator,
            TenantService tenantService
    ) {
        this.endpointRepository = endpointRepository;
        this.secretGenerator = secretGenerator;
        this.urlValidator = urlValidator;
        this.tenantService = tenantService;
    }

    @Transactional
    public EndpointResponse createEndpoint(UUID tenantId, CreateEndpointRequest request) {
        tenantService.getTenantEntity(tenantId);
        urlValidator.validate(request.url());
        WebhookEndpoint endpoint = endpointRepository.save(
                new WebhookEndpoint(tenantId, request.url(), secretGenerator.generate())
        );
        return EndpointResponse.from(endpoint);
    }

    @Transactional(readOnly = true)
    public List<EndpointResponse> listEndpoints(UUID tenantId) {
        tenantService.getTenantEntity(tenantId);
        return endpointRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .map(EndpointResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public EndpointResponse getEndpoint(UUID tenantId, UUID endpointId) {
        return EndpointResponse.from(findEndpoint(tenantId, endpointId));
    }

    @Transactional
    public EndpointResponse updateEndpoint(UUID tenantId, UUID endpointId, UpdateEndpointRequest request) {
        WebhookEndpoint endpoint = findEndpoint(tenantId, endpointId);
        urlValidator.validate(request.url());
        endpoint.setUrl(request.url());
        return EndpointResponse.from(endpoint);
    }

    @Transactional
    public EndpointResponse setEndpointActive(UUID tenantId, UUID endpointId, boolean active) {
        WebhookEndpoint endpoint = findEndpoint(tenantId, endpointId);
        endpoint.setActive(active);
        return EndpointResponse.from(endpoint);
    }

    @Transactional
    public void deleteEndpoint(UUID tenantId, UUID endpointId) {
        WebhookEndpoint endpoint = findEndpoint(tenantId, endpointId);
        endpointRepository.delete(endpoint);
    }

    private WebhookEndpoint findEndpoint(UUID tenantId, UUID endpointId) {
        return endpointRepository.findByIdAndTenantId(endpointId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Endpoint not found: " + endpointId));
    }
}
