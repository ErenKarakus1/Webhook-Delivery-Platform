package com.webhooks.management.endpoint;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/tenants/{tenantId}/endpoints")
public class EndpointController {
    private final EndpointService endpointService;

    public EndpointController(EndpointService endpointService) {
        this.endpointService = endpointService;
    }

    @PostMapping
    ResponseEntity<EndpointResponse> createEndpoint(
            @PathVariable UUID tenantId,
            @Valid @RequestBody CreateEndpointRequest request
    ) {
        EndpointResponse endpoint = endpointService.createEndpoint(tenantId, request);
        return ResponseEntity.created(URI.create("/tenants/" + tenantId + "/endpoints/" + endpoint.id())).body(endpoint);
    }

    @GetMapping
    List<EndpointResponse> listEndpoints(@PathVariable UUID tenantId) {
        return endpointService.listEndpoints(tenantId);
    }

    @GetMapping("/{endpointId}")
    EndpointResponse getEndpoint(@PathVariable UUID tenantId, @PathVariable UUID endpointId) {
        return endpointService.getEndpoint(tenantId, endpointId);
    }

    @PatchMapping("/{endpointId}")
    EndpointResponse updateEndpoint(
            @PathVariable UUID tenantId,
            @PathVariable UUID endpointId,
            @Valid @RequestBody UpdateEndpointRequest request
    ) {
        return endpointService.updateEndpoint(tenantId, endpointId, request);
    }

    @PatchMapping("/{endpointId}/activate")
    EndpointResponse activateEndpoint(@PathVariable UUID tenantId, @PathVariable UUID endpointId) {
        return endpointService.setEndpointActive(tenantId, endpointId, true);
    }

    @PatchMapping("/{endpointId}/deactivate")
    EndpointResponse deactivateEndpoint(@PathVariable UUID tenantId, @PathVariable UUID endpointId) {
        return endpointService.setEndpointActive(tenantId, endpointId, false);
    }

    @DeleteMapping("/{endpointId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteEndpoint(@PathVariable UUID tenantId, @PathVariable UUID endpointId) {
        endpointService.deleteEndpoint(tenantId, endpointId);
    }
}
