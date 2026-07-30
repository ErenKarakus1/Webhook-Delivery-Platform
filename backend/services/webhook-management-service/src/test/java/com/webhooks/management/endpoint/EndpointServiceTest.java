package com.webhooks.management.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import com.webhooks.management.common.DuplicateResourceException;
import com.webhooks.management.common.ResourceNotFoundException;
import com.webhooks.management.tenant.Tenant;
import com.webhooks.management.tenant.TenantService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class EndpointServiceTest {
    private final EndpointRepository endpointRepository = Mockito.mock(EndpointRepository.class);
    private final EndpointSecretGenerator secretGenerator = Mockito.mock(EndpointSecretGenerator.class);
    private final EndpointUrlValidator urlValidator = Mockito.mock(EndpointUrlValidator.class);
    private final TenantService tenantService = Mockito.mock(TenantService.class);
    private final EndpointService endpointService = new EndpointService(
            endpointRepository,
            secretGenerator,
            urlValidator,
            tenantService
    );

    @BeforeEach
    void setUp() {
        Mockito.reset(endpointRepository, secretGenerator, urlValidator, tenantService);
    }

    @Test
    void createsEndpointForExistingTenant() {
        UUID tenantId = UUID.randomUUID();
        when(tenantService.getTenantEntity(tenantId)).thenReturn(new Tenant("Acme"));
        when(secretGenerator.generate()).thenReturn("endpoint-secret");
        when(endpointRepository.save(any(WebhookEndpoint.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EndpointResponse response = endpointService.createEndpoint(
                tenantId,
                new CreateEndpointRequest("https://example.com/webhooks")
        );

        assertThat(response.tenantId()).isEqualTo(tenantId);
        assertThat(response.url()).isEqualTo("https://example.com/webhooks");
        assertThat(response.active()).isTrue();
        verify(urlValidator).validate("https://example.com/webhooks");
        ArgumentCaptor<WebhookEndpoint> endpointCaptor = ArgumentCaptor.forClass(WebhookEndpoint.class);
        verify(endpointRepository).save(endpointCaptor.capture());
        assertThat(endpointCaptor.getValue().getSecret()).isEqualTo("endpoint-secret");
    }

    @Test
    void propagatesMissingTenantWhenCreatingEndpoint() {
        UUID tenantId = UUID.randomUUID();
        when(tenantService.getTenantEntity(tenantId)).thenThrow(new ResourceNotFoundException("Tenant not found"));

        assertThatThrownBy(() -> endpointService.createEndpoint(
                tenantId,
                new CreateEndpointRequest("https://example.com/webhooks")
        )).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void rejectsDuplicateActiveEndpointUrlForTenant() {
        UUID tenantId = UUID.randomUUID();
        when(tenantService.getTenantEntity(tenantId)).thenReturn(new Tenant("Acme"));
        when(endpointRepository.existsByTenantIdAndUrlIgnoreCaseAndActiveTrue(
                tenantId,
                "https://example.com/webhooks"
        )).thenReturn(true);

        assertThatThrownBy(() -> endpointService.createEndpoint(
                tenantId,
                new CreateEndpointRequest("https://example.com/webhooks")
        )).isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("active endpoint");

        verify(endpointRepository, never()).save(any(WebhookEndpoint.class));
    }

    @Test
    void updatesEndpointUrl() {
        UUID tenantId = UUID.randomUUID();
        WebhookEndpoint endpoint = new WebhookEndpoint(tenantId, "https://old.example.com/webhooks", "secret");
        when(endpointRepository.findByIdAndTenantId(endpoint.getId(), tenantId)).thenReturn(Optional.of(endpoint));

        EndpointResponse response = endpointService.updateEndpoint(
                tenantId,
                endpoint.getId(),
                new UpdateEndpointRequest("https://new.example.com/webhooks")
        );

        assertThat(response.url()).isEqualTo("https://new.example.com/webhooks");
        verify(urlValidator).validate("https://new.example.com/webhooks");
    }

    @Test
    void rejectsEndpointUrlUpdateThatDuplicatesActiveEndpoint() {
        UUID tenantId = UUID.randomUUID();
        WebhookEndpoint endpoint = new WebhookEndpoint(tenantId, "https://old.example.com/webhooks", "secret");
        when(endpointRepository.findByIdAndTenantId(endpoint.getId(), tenantId)).thenReturn(Optional.of(endpoint));
        when(endpointRepository.existsByTenantIdAndUrlIgnoreCaseAndActiveTrueAndIdNot(
                tenantId,
                "https://new.example.com/webhooks",
                endpoint.getId()
        )).thenReturn(true);

        assertThatThrownBy(() -> endpointService.updateEndpoint(
                tenantId,
                endpoint.getId(),
                new UpdateEndpointRequest("https://new.example.com/webhooks")
        )).isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void changesEndpointActiveState() {
        UUID tenantId = UUID.randomUUID();
        WebhookEndpoint endpoint = new WebhookEndpoint(tenantId, "https://example.com/webhooks", "secret");
        when(endpointRepository.findByIdAndTenantId(endpoint.getId(), tenantId)).thenReturn(Optional.of(endpoint));

        EndpointResponse response = endpointService.setEndpointActive(tenantId, endpoint.getId(), false);

        assertThat(response.active()).isFalse();
        assertThat(endpoint.isActive()).isFalse();
    }

    @Test
    void rejectsReactivationThatDuplicatesActiveEndpointUrl() {
        UUID tenantId = UUID.randomUUID();
        WebhookEndpoint endpoint = new WebhookEndpoint(tenantId, "https://example.com/webhooks", "secret");
        endpoint.setActive(false);
        when(endpointRepository.findByIdAndTenantId(endpoint.getId(), tenantId)).thenReturn(Optional.of(endpoint));
        when(endpointRepository.existsByTenantIdAndUrlIgnoreCaseAndActiveTrueAndIdNot(
                tenantId,
                "https://example.com/webhooks",
                endpoint.getId()
        )).thenReturn(true);

        assertThatThrownBy(() -> endpointService.setEndpointActive(tenantId, endpoint.getId(), true))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void deletesTenantScopedEndpoint() {
        UUID tenantId = UUID.randomUUID();
        WebhookEndpoint endpoint = new WebhookEndpoint(tenantId, "https://example.com/webhooks", "secret");
        when(endpointRepository.findByIdAndTenantId(endpoint.getId(), tenantId)).thenReturn(Optional.of(endpoint));

        endpointService.deleteEndpoint(tenantId, endpoint.getId());

        verify(endpointRepository).delete(endpoint);
    }

    @Test
    void throwsWhenEndpointDoesNotBelongToTenant() {
        UUID tenantId = UUID.randomUUID();
        UUID endpointId = UUID.randomUUID();
        when(endpointRepository.findByIdAndTenantId(endpointId, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> endpointService.getEndpoint(tenantId, endpointId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Endpoint not found");
    }
}
