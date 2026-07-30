package com.webhooks.management.subscription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import com.webhooks.management.common.DuplicateResourceException;
import com.webhooks.management.common.ResourceNotFoundException;
import com.webhooks.management.endpoint.EndpointResponse;
import com.webhooks.management.endpoint.EndpointService;
import com.webhooks.management.tenant.Tenant;
import com.webhooks.management.tenant.TenantService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class SubscriptionServiceTest {
    private final EndpointService endpointService = Mockito.mock(EndpointService.class);
    private final SubscriptionRepository subscriptionRepository = Mockito.mock(SubscriptionRepository.class);
    private final TenantService tenantService = Mockito.mock(TenantService.class);
    private final SubscriptionService subscriptionService = new SubscriptionService(
            endpointService,
            subscriptionRepository,
            tenantService
    );

    @BeforeEach
    void setUp() {
        Mockito.reset(endpointService, subscriptionRepository, tenantService);
    }

    @Test
    void createsSubscriptionForTenantEndpoint() {
        UUID tenantId = UUID.randomUUID();
        UUID endpointId = UUID.randomUUID();
        when(tenantService.getTenantEntity(tenantId)).thenReturn(new Tenant("Acme"));
        when(endpointService.getEndpoint(tenantId, endpointId)).thenReturn(endpointResponse(tenantId, endpointId));
        when(subscriptionRepository.save(any(WebhookSubscription.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SubscriptionResponse response = subscriptionService.createSubscription(
                tenantId,
                new CreateSubscriptionRequest(endpointId, "order.created")
        );

        assertThat(response.tenantId()).isEqualTo(tenantId);
        assertThat(response.endpointId()).isEqualTo(endpointId);
        assertThat(response.eventType()).isEqualTo("order.created");
        assertThat(response.active()).isTrue();
        ArgumentCaptor<WebhookSubscription> subscriptionCaptor = ArgumentCaptor.forClass(WebhookSubscription.class);
        verify(subscriptionRepository).save(subscriptionCaptor.capture());
        assertThat(subscriptionCaptor.getValue().getEndpointId()).isEqualTo(endpointId);
    }

    @Test
    void propagatesMissingEndpointWhenCreatingSubscription() {
        UUID tenantId = UUID.randomUUID();
        UUID endpointId = UUID.randomUUID();
        when(tenantService.getTenantEntity(tenantId)).thenReturn(new Tenant("Acme"));
        when(endpointService.getEndpoint(tenantId, endpointId)).thenThrow(new ResourceNotFoundException("Endpoint not found"));

        assertThatThrownBy(() -> subscriptionService.createSubscription(
                tenantId,
                new CreateSubscriptionRequest(endpointId, "order.created")
        )).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void rejectsDuplicateSubscriptionForEndpointAndEventType() {
        UUID tenantId = UUID.randomUUID();
        UUID endpointId = UUID.randomUUID();
        when(tenantService.getTenantEntity(tenantId)).thenReturn(new Tenant("Acme"));
        when(endpointService.getEndpoint(tenantId, endpointId)).thenReturn(endpointResponse(tenantId, endpointId));
        when(subscriptionRepository.existsByEndpointIdAndEventTypeIgnoreCase(endpointId, "order.created")).thenReturn(true);

        assertThatThrownBy(() -> subscriptionService.createSubscription(
                tenantId,
                new CreateSubscriptionRequest(endpointId, "order.created")
        )).isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("already has a subscription");

        verify(subscriptionRepository, never()).save(any(WebhookSubscription.class));
    }

    @Test
    void updatesSubscriptionEndpointAndEventType() {
        UUID tenantId = UUID.randomUUID();
        UUID endpointId = UUID.randomUUID();
        UUID nextEndpointId = UUID.randomUUID();
        WebhookSubscription subscription = new WebhookSubscription(tenantId, endpointId, "order.created");
        when(subscriptionRepository.findByIdAndTenantId(subscription.getId(), tenantId)).thenReturn(Optional.of(subscription));
        when(endpointService.getEndpoint(tenantId, nextEndpointId)).thenReturn(endpointResponse(tenantId, nextEndpointId));

        SubscriptionResponse response = subscriptionService.updateSubscription(
                tenantId,
                subscription.getId(),
                new UpdateSubscriptionRequest(nextEndpointId, "invoice.paid")
        );

        assertThat(response.endpointId()).isEqualTo(nextEndpointId);
        assertThat(response.eventType()).isEqualTo("invoice.paid");
        assertThat(subscription.getEndpointId()).isEqualTo(nextEndpointId);
        assertThat(subscription.getEventType()).isEqualTo("invoice.paid");
    }

    @Test
    void rejectsSubscriptionUpdateThatDuplicatesEndpointAndEventType() {
        UUID tenantId = UUID.randomUUID();
        UUID endpointId = UUID.randomUUID();
        UUID nextEndpointId = UUID.randomUUID();
        WebhookSubscription subscription = new WebhookSubscription(tenantId, endpointId, "order.created");
        when(subscriptionRepository.findByIdAndTenantId(subscription.getId(), tenantId)).thenReturn(Optional.of(subscription));
        when(endpointService.getEndpoint(tenantId, nextEndpointId)).thenReturn(endpointResponse(tenantId, nextEndpointId));
        when(subscriptionRepository.existsByEndpointIdAndEventTypeIgnoreCaseAndIdNot(
                nextEndpointId,
                "invoice.paid",
                subscription.getId()
        )).thenReturn(true);

        assertThatThrownBy(() -> subscriptionService.updateSubscription(
                tenantId,
                subscription.getId(),
                new UpdateSubscriptionRequest(nextEndpointId, "invoice.paid")
        )).isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void changesSubscriptionActiveState() {
        UUID tenantId = UUID.randomUUID();
        WebhookSubscription subscription = new WebhookSubscription(tenantId, UUID.randomUUID(), "order.created");
        when(subscriptionRepository.findByIdAndTenantId(subscription.getId(), tenantId)).thenReturn(Optional.of(subscription));

        SubscriptionResponse response = subscriptionService.setSubscriptionActive(tenantId, subscription.getId(), false);

        assertThat(response.active()).isFalse();
        assertThat(subscription.isActive()).isFalse();
    }

    @Test
    void deletesTenantScopedSubscription() {
        UUID tenantId = UUID.randomUUID();
        WebhookSubscription subscription = new WebhookSubscription(tenantId, UUID.randomUUID(), "order.created");
        when(subscriptionRepository.findByIdAndTenantId(subscription.getId(), tenantId)).thenReturn(Optional.of(subscription));

        subscriptionService.deleteSubscription(tenantId, subscription.getId());

        verify(subscriptionRepository).delete(subscription);
    }

    @Test
    void throwsWhenSubscriptionDoesNotBelongToTenant() {
        UUID tenantId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        when(subscriptionRepository.findByIdAndTenantId(subscriptionId, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> subscriptionService.getSubscription(tenantId, subscriptionId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Subscription not found");
    }

    private EndpointResponse endpointResponse(UUID tenantId, UUID endpointId) {
        return new EndpointResponse(endpointId, tenantId, "https://example.com/webhooks", true, null);
    }
}
