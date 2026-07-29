package com.webhooks.management.subscription;

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
@RequestMapping("/tenants/{tenantId}/subscriptions")
public class SubscriptionController {
    private final SubscriptionService subscriptionService;

    public SubscriptionController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @PostMapping
    ResponseEntity<SubscriptionResponse> createSubscription(
            @PathVariable UUID tenantId,
            @Valid @RequestBody CreateSubscriptionRequest request
    ) {
        SubscriptionResponse subscription = subscriptionService.createSubscription(tenantId, request);
        return ResponseEntity.created(URI.create("/tenants/" + tenantId + "/subscriptions/" + subscription.id()))
                .body(subscription);
    }

    @GetMapping
    List<SubscriptionResponse> listSubscriptions(@PathVariable UUID tenantId) {
        return subscriptionService.listSubscriptions(tenantId);
    }

    @GetMapping("/{subscriptionId}")
    SubscriptionResponse getSubscription(@PathVariable UUID tenantId, @PathVariable UUID subscriptionId) {
        return subscriptionService.getSubscription(tenantId, subscriptionId);
    }

    @PatchMapping("/{subscriptionId}")
    SubscriptionResponse updateSubscription(
            @PathVariable UUID tenantId,
            @PathVariable UUID subscriptionId,
            @Valid @RequestBody UpdateSubscriptionRequest request
    ) {
        return subscriptionService.updateSubscription(tenantId, subscriptionId, request);
    }

    @PatchMapping("/{subscriptionId}/activate")
    SubscriptionResponse activateSubscription(@PathVariable UUID tenantId, @PathVariable UUID subscriptionId) {
        return subscriptionService.setSubscriptionActive(tenantId, subscriptionId, true);
    }

    @PatchMapping("/{subscriptionId}/deactivate")
    SubscriptionResponse deactivateSubscription(@PathVariable UUID tenantId, @PathVariable UUID subscriptionId) {
        return subscriptionService.setSubscriptionActive(tenantId, subscriptionId, false);
    }

    @DeleteMapping("/{subscriptionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteSubscription(@PathVariable UUID tenantId, @PathVariable UUID subscriptionId) {
        subscriptionService.deleteSubscription(tenantId, subscriptionId);
    }
}
