package com.webhooks.management.endpoint;

import com.webhooks.management.common.DuplicateResourceException;
import com.webhooks.management.common.ResourceNotFoundException;
import com.webhooks.management.subscription.SubscriptionRepository;
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
    private final SubscriptionRepository subscriptionRepository;
    private final TenantService tenantService;

    public EndpointService(
            EndpointRepository endpointRepository,
            EndpointSecretGenerator secretGenerator,
            EndpointUrlValidator urlValidator,
            SubscriptionRepository subscriptionRepository,
            TenantService tenantService
    ) {
        this.endpointRepository = endpointRepository;
        this.secretGenerator = secretGenerator;
        this.urlValidator = urlValidator;
        this.subscriptionRepository = subscriptionRepository;
        this.tenantService = tenantService;
    }

    @Transactional
    public EndpointResponse createEndpoint(UUID tenantId, CreateEndpointRequest request) {
        tenantService.getTenantEntity(tenantId);
        urlValidator.validate(request.url());
        if (endpointRepository.existsByTenantIdAndUrlIgnoreCaseAndActiveTrueAndDeletedAtIsNull(tenantId, request.url())) {
            throw new DuplicateResourceException("An active endpoint already exists for this URL");
        }
        WebhookEndpoint endpoint = endpointRepository.save(
                new WebhookEndpoint(tenantId, request.url(), secretGenerator.generate())
        );
        return EndpointResponse.from(endpoint);
    }

    @Transactional(readOnly = true)
    public List<EndpointResponse> listEndpoints(UUID tenantId) {
        tenantService.getTenantEntity(tenantId);
        return endpointRepository.findByTenantIdAndDeletedAtIsNullOrderByCreatedAtDesc(tenantId).stream()
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
        if (endpoint.isActive()
                && endpointRepository.existsByTenantIdAndUrlIgnoreCaseAndActiveTrueAndDeletedAtIsNullAndIdNot(tenantId, request.url(), endpointId)) {
            throw new DuplicateResourceException("An active endpoint already exists for this URL");
        }
        endpoint.setUrl(request.url());
        return EndpointResponse.from(endpoint);
    }

    @Transactional
    public EndpointResponse setEndpointActive(UUID tenantId, UUID endpointId, boolean active) {
        WebhookEndpoint endpoint = findEndpoint(tenantId, endpointId);
        if (active && endpointRepository.existsByTenantIdAndUrlIgnoreCaseAndActiveTrueAndDeletedAtIsNullAndIdNot(tenantId, endpoint.getUrl(), endpointId)) {
            throw new DuplicateResourceException("An active endpoint already exists for this URL");
        }
        endpoint.setActive(active);
        return EndpointResponse.from(endpoint);
    }

    @Transactional
    public void deleteEndpoint(UUID tenantId, UUID endpointId) {
        WebhookEndpoint endpoint = findEndpoint(tenantId, endpointId);
        endpoint.markDeleted();
        subscriptionRepository.softDeleteByTenantIdAndEndpointId(tenantId, endpointId);
    }

    private WebhookEndpoint findEndpoint(UUID tenantId, UUID endpointId) {
        return endpointRepository.findByIdAndTenantIdAndDeletedAtIsNull(endpointId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Endpoint not found: " + endpointId));
    }
}
