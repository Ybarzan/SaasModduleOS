package com.fleethub.controller;

import com.fleethub.model.ShipmentItem;
import com.fleethub.model.ShipmentOrder;
import com.fleethub.service.InternalIntegrationService;
import com.fleethub.service.ShipmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Lecture d'expéditions pour compliance-engine / Logistix (dérivation de
 * documents douaniers / lignes de réception) -- jamais un utilisateur
 * connecté. permitAll côté Spring Security (voir SecurityConfig,
 * "/api/internal/**") : l'authentification se fait ici, via
 * X-Internal-Integration-Key plutôt qu'un JWT (ces hubs n'en ont pas un de
 * fleet-hub), résolue en société par {@link InternalIntegrationService},
 * jamais depuis un identifiant fourni par l'appelant.
 */
@RestController
@RequestMapping("/api/internal/shipments")
@RequiredArgsConstructor
@Tag(name = "Intégration interne", description = "Lecture d'expéditions par compliance-engine / Logistix")
public class InternalShipmentController {

    private final InternalIntegrationService internalIntegrationService;
    private final ShipmentService shipmentService;

    @GetMapping("/{id}")
    @Operation(summary = "Détail d'une expédition (compliance-engine / Logistix)",
            description = "Appel machine-à-machine, authentifié par X-Internal-Integration-Key")
    @ApiResponse(responseCode = "200", description = "Expédition retournée")
    @ApiResponse(responseCode = "401", description = "Clé manquante, invalide, ou partage désactivé")
    @ApiResponse(responseCode = "404", description = "Expédition introuvable pour cette société")
    public ShipmentOrder getShipment(
            @PathVariable UUID id,
            @RequestHeader(value = "X-Internal-Integration-Key", required = false) String apiKey) {
        Long companyId = internalIntegrationService.resolveCompanyId(apiKey);
        return shipmentService.getShipmentWithTracking(id, companyId);
    }

    @GetMapping("/{id}/items")
    @Operation(summary = "Articles d'une expédition (compliance-engine / Logistix)",
            description = "Appel machine-à-machine, authentifié par X-Internal-Integration-Key")
    @ApiResponse(responseCode = "200", description = "Articles retournés")
    @ApiResponse(responseCode = "401", description = "Clé manquante, invalide, ou partage désactivé")
    public List<ShipmentItem> getShipmentItems(
            @PathVariable UUID id,
            @RequestHeader(value = "X-Internal-Integration-Key", required = false) String apiKey) {
        Long companyId = internalIntegrationService.resolveCompanyId(apiKey);
        return shipmentService.getShipmentItems(id, companyId);
    }
}
