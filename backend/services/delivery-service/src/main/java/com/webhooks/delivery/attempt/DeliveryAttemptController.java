package com.webhooks.delivery.attempt;

import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DeliveryAttemptController {
    private final DeliveryAttemptRepository attemptRepository;

    public DeliveryAttemptController(DeliveryAttemptRepository attemptRepository) {
        this.attemptRepository = attemptRepository;
    }

    @GetMapping("/attempts")
    List<DeliveryAttemptResponse> listAttempts(
            @RequestParam(required = false) UUID eventId,
            @RequestParam(required = false) UUID endpointId
    ) {
        if (eventId != null) {
            return attemptRepository.findByEventIdOrderByAttemptNumberAsc(eventId).stream()
                    .map(DeliveryAttemptResponse::from)
                    .toList();
        }
        if (endpointId != null) {
            return attemptRepository.findByEndpointIdOrderByAttemptedAtDesc(endpointId).stream()
                    .map(DeliveryAttemptResponse::from)
                    .toList();
        }
        return attemptRepository.findAll().stream()
                .map(DeliveryAttemptResponse::from)
                .toList();
    }
}
