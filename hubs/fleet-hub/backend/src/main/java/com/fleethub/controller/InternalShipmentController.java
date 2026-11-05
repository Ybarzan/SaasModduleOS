package com.fleethub.controller;

import com.fleethub.dto.ExternalShipmentCreationRequest;
import com.fleethub.dto.ShipmentOrderDTO;
import com.fleethub.dto.internal.InternalShipmentDto;
import com.fleethub.dto.internal.InternalShipmentItemDto;
import com.fleethub.security.TenantContext;
import com.fleethub.service.InternalIntegrationService;
import com.fleethub.service.InternalShipmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Lecture ET création d'expéditions pour les autres hubs (compliance-engine :
 * documents douaniers, création brouillon depuis une commande e-commerce ou un
 * email, ADR-0002 ; Logistix : lignes de réception ; Finance : reporting CSRD) --
 * jamais un utilisateur connecté.
 * <p>
 * L'authentification (clé X-Internal-Integration-Key), la société et les droits de
 * la clé sont établis en amont par InternalApiKeyFilter : ici, {@link TenantContext}
 * porte toujours la société de la clé. Les réponses sont des DTO de contrat
 * ({@link InternalShipmentDto}), jamais les entités JPA.
 */
@RestController
@RequestMapping("/api/internal/shipments")
@RequiredArgsConstructor
@Tag(name = "Intégration interne", description = "Lecture et création d'expéditions par les autres hubs")
public class InternalShipmentController {

    private final InternalIntegrationService internalIntegrationService;
    private final InternalShipmentService internalShipmentService;

    @GetMapping
    @Operation(summary = "Expéditions récentes, ou recherche par texte",
            description = "Sans paramètre q, les 50 plus récentes (date de création descendante) ; "
                    + "avec q, recherche sur le numéro de commande ou le nom du destinataire "
                    + "(jusqu'à 20 résultats, pour l'autocomplétion -- pas de pagination ici)")
    @ApiResponse(responseCode = "200", description = "Expéditions retournées")
    @ApiResponse(responseCode = "401", description = "Clé manquante, invalide, ou partage désactivé")
    public List<InternalShipmentDto> listRecentShipments(@RequestParam(value = "q", required = false) String query) {
        return internalShipmentService.recentOrSearch(TenantContext.require().getId(), query);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Détail d'une expédition, avec ses événements de suivi")
    @ApiResponse(responseCode = "200", description = "Expédition retournée")
    @ApiResponse(responseCode = "401", description = "Clé manquante, invalide, ou partage désactivé")
    @ApiResponse(responseCode = "404", description = "Expédition introuvable pour cette société")
    public InternalShipmentDto getShipment(@PathVariable UUID id) {
        return internalShipmentService.detail(id, TenantContext.require().getId());
    }

    @GetMapping("/{id}/items")
    @Operation(summary = "Articles d'une expédition")
    @ApiResponse(responseCode = "200", description = "Articles retournés")
    @ApiResponse(responseCode = "401", description = "Clé manquante, invalide, ou partage désactivé")
    public List<InternalShipmentItemDto> getShipmentItems(@PathVariable UUID id) {
        return internalShipmentService.items(id, TenantContext.require().getId());
    }

    @PostMapping
    @Operation(summary = "Créer une expédition brouillon depuis une source externe (compliance-engine)",
            description = "Nécessite une clé ayant le droit de création ET l'opt-in société « création d'expédition "
                    + "externe » (voir /api/internal-integrations/shipment-creation), séparé du partage en lecture "
                    + "(ADR-0002). Toujours créée en statut DRAFT, jamais de transporteur/camion/tarif/client assigné "
                    + "depuis cet appel -- un humain complète depuis l'UI fleet-hub normale.")
    @ApiResponse(responseCode = "201", description = "Expédition créée (brouillon)")
    @ApiResponse(responseCode = "400", description = "Champs obligatoires manquants (shipperName/consigneeName)")
    @ApiResponse(responseCode = "401", description = "Clé manquante, invalide, ou partage en lecture désactivé")
    @ApiResponse(responseCode = "403", description = "Clé sans droit de création, ou opt-in société non activé")
    public ResponseEntity<InternalShipmentDto> createShipmentFromExternalSource(
            @Valid @RequestBody ExternalShipmentCreationRequest request) {
        Long companyId = TenantContext.require().getId();
        internalIntegrationService.requireShipmentCreationAllowed(companyId);
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
                .goodsValue(request.getGoodsValue() != null ? BigDecimal.valueOf(request.getGoodsValue()) : null)
                .currency(request.getCurrency())
                .weightKg(request.getWeightKg())
                .volumeM3(request.getVolumeM3())
                .packagesCount(request.getPackagesCount())
                .build();
        return ResponseEntity.status(HttpStatus.CREATED).body(internalShipmentService.createDraft(dto, companyId));
    }
}
