package com.webhooks.management.subscription;

import com.webhooks.management.common.ResourceNotFoundException;
import com.webhooks.management.endpoint.EndpointService;
import com.webhooks.management.tenant.TenantService;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubscriptionService {
    private final EndpointService endpointService;
    private final SubscriptionRepository subscriptionRepository;
    private final TenantService tenantService;

    public SubscriptionService(
            EndpointService endpointService,
            SubscriptionRepository subscriptionRepository,
            TenantService tenantService
    ) {
        this.endpointService = endpointService;
        this.subscriptionRepository = subscriptionRepository;
        this.tenantService = tenantService;
    }

    @Transactional
    public SubscriptionResponse createSubscription(UUID tenantId, CreateSubscriptionRequest request) {
        tenantService.getTenantEntity(tenantId);
        endpointService.getEndpoint(tenantId, request.endpointId());

        WebhookSubscription subscription = subscriptionRepository.save(
                new WebhookSubscription(tenantId, request.endpointId(), request.eventType())
        );
        return SubscriptionResponse.from(subscription);
    }

    @Transactional(readOnly = true)
    public List<SubscriptionResponse> listSubscriptions(UUID tenantId) {
        tenantService.getTenantEntity(tenantId);
        return subscriptionRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .map(SubscriptionResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public SubscriptionResponse getSubscription(UUID tenantId, UUID subscriptionId) {
        return SubscriptionResponse.from(findSubscription(tenantId, subscriptionId));
    }

    @Transactional
    public SubscriptionResponse updateSubscription(UUID tenantId, UUID subscriptionId, UpdateSubscriptionRequest request) {
        WebhookSubscription subscription = findSubscription(tenantId, subscriptionId);
        endpointService.getEndpoint(tenantId, request.endpointId());
        subscription.setEndpointId(request.endpointId());
        subscription.setEventType(request.eventType());
        return SubscriptionResponse.from(subscription);
    }

    @Transactional
    public SubscriptionResponse setSubscriptionActive(UUID tenantId, UUID subscriptionId, boolean active) {
        WebhookSubscription subscription = findSubscription(tenantId, subscriptionId);
        subscription.setActive(active);
        return SubscriptionResponse.from(subscription);
    }

    @Transactional
    public void deleteSubscription(UUID tenantId, UUID subscriptionId) {
        subscriptionRepository.delete(findSubscription(tenantId, subscriptionId));
    }

    private WebhookSubscription findSubscription(UUID tenantId, UUID subscriptionId) {
        return subscriptionRepository.findByIdAndTenantId(subscriptionId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription not found: " + subscriptionId));
    }
}
