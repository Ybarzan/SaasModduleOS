package com.fleethub.controller;

import com.fleethub.dto.ExternalShipmentCreationRequest;
import com.fleethub.dto.ShipmentOrderDTO;
import com.fleethub.model.ShipmentItem;
import com.fleethub.model.ShipmentOrder;
import com.fleethub.service.InternalIntegrationService;
import com.fleethub.service.ShipmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Lecture ET création d'expéditions pour compliance-engine / Logistix
 * (dérivation de documents douaniers / lignes de réception ; création
 * brouillon depuis une commande e-commerce ou un email, ADR-0002) -- jamais
 * un utilisateur connecté. permitAll côté Spring Security (voir
 * SecurityConfig, "/api/internal/**") : l'authentification se fait ici, via
 * X-Internal-Integration-Key plutôt qu'un JWT (ces hubs n'en ont pas un de
 * fleet-hub), résolue en société par {@link InternalIntegrationService},
 * jamais depuis un identifiant fourni par l'appelant.
 */
@RestController
@RequestMapping("/api/internal/shipments")
@RequiredArgsConstructor
@Tag(name = "Intégration interne", description = "Lecture et création d'expéditions par compliance-engine / Logistix")
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

    @PostMapping
    @Operation(summary = "Créer une expédition brouillon depuis une source externe (compliance-engine)",
            description = "Appel machine-à-machine, authentifié par X-Internal-Integration-Key -- nécessite en plus "
                    + "l'opt-in dédié \"création d'expédition externe\" (voir /api/internal-integrations/shipment-creation), "
                    + "séparé du partage en lecture (ADR-0002). Toujours créée en statut DRAFT, jamais de transporteur/camion/tarif/"
                    + "client assigné depuis cet appel -- un humain complète depuis l'UI fleet-hub normale.")
    @ApiResponse(responseCode = "201", description = "Expédition créée (brouillon)")
    @ApiResponse(responseCode = "400", description = "Champs obligatoires manquants (shipperName/consigneeName)")
    @ApiResponse(responseCode = "401", description = "Clé manquante, invalide, ou partage en lecture désactivé")
    @ApiResponse(responseCode = "403", description = "Création d'expédition externe non activée pour cette société")
    public ResponseEntity<ShipmentOrder> createShipmentFromExternalSource(
            @Valid @RequestBody ExternalShipmentCreationRequest request,
            @RequestHeader(value = "X-Internal-Integration-Key", required = false) String apiKey) {
        Long companyId = internalIntegrationService.resolveCompanyIdForShipmentCreation(apiKey);
        ShipmentOrderDTO dto = ShipmentOrderDTO.builder()
                .shipperName(request.getShipperName())
                .shipperAddress(request.getShipperAddress())
                .shipperCity(request.getShipperCity())
                .shipperCountry(request.getShipperCountry())
                .shipperPostalCode(request.getShipperPostalCode())
                .consigneeName(request.getConsigneeName())
                .consigneeAddress(request.getConsigneeAddress())
                .consigneeCity(request.getConsigneeCity())
                .consigneeCountry(request.getConsigneeCountry())
                .consigneePostalCode(request.getConsigneePostalCode())
                .goodsDescription(request.getGoodsDescription())
                .goodsValue(request.getGoodsValue())
                .currency(request.getCurrency())
                .weightKg(request.getWeightKg())
                .volumeM3(request.getVolumeM3())
                .packagesCount(request.getPackagesCount())
                .build();
        ShipmentOrder created = shipmentService.createShipment(dto, null, companyId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}
