package com.incokalk.controller.integration;

import com.incokalk.dto.integration.fleethub.FleetHubShipmentEventRequest;
import com.incokalk.service.integration.fleethub.FleetHubWebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Réception des événements d'expédition (création / changement de statut)
 * poussés par fleet-hub -- réalimente le moteur de règles (voir
 * FleetHubWebhookService, EventOutboxProcessor), resté sans producteur
 * depuis le retrait du domaine Transport. permitAll côté Spring Security
 * (voir SecurityConfig, "/v1/webhooks/**") : l'authentification se fait ici,
 * via X-FleetHub-Webhook-Key plutôt qu'un JWT, résolue en société par
 * {@link FleetHubWebhookService#resolveCompanyId}, jamais depuis un
 * identifiant fourni par l'appelant.
 */
@RestController
@RequestMapping("/v1/webhooks/fleethub")
@RequiredArgsConstructor
@Tag(name = "Webhook fleet-hub", description = "Réception des événements d'expédition depuis fleet-hub")
public class FleetHubEventWebhookController {

    private final FleetHubWebhookService fleetHubWebhookService;

    @PostMapping("/shipment-events")
    @Operation(summary = "Événement d'expédition (fleet-hub)",
            description = "Appel machine-à-machine, authentifié par X-FleetHub-Webhook-Key")
    @ApiResponse(responseCode = "202", description = "Événement accepté, traité de façon asynchrone")
    @ApiResponse(responseCode = "401", description = "Clé manquante, invalide, ou réception désactivée")
    public ResponseEntity<Void> handleShipmentEvent(
            @RequestHeader(value = "X-FleetHub-Webhook-Key", required = false) String apiKey,
            @Valid @RequestBody FleetHubShipmentEventRequest request) {
        UUID companyId = fleetHubWebhookService.resolveCompanyId(apiKey);
        fleetHubWebhookService.recordEvent(companyId, request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }
}
