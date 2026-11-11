package com.fleethub.controller;

import com.fleethub.dto.ExternalShipmentCreationSettingsDto;
import com.fleethub.dto.GeneratedIntegrationKeyDto;
import com.fleethub.dto.InternalIntegrationSettingsDto;
import com.fleethub.security.TenantContext;
import com.fleethub.service.InternalIntegrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * Partage opt-in des données de la société avec les autres hubs (compliance-engine,
 * Logistix, Finance, portail Praxio). Réservé ADMIN (voir SecurityConfig,
 * "/api/internal-integrations/**") -- contrairement à "/api/internal/**", appelé par
 * les hubs eux-mêmes avec leur clé (InternalApiKeyFilter).
 */
@RestController
@RequestMapping("/api/internal-integrations")
@RequiredArgsConstructor
@Tag(name = "Intégration interne", description = "Partage opt-in vers les autres hubs : interrupteur et clés par hub")
public class InternalIntegrationController {

    private final InternalIntegrationService internalIntegrationService;

    @GetMapping("/settings")
    @Operation(summary = "Statut du partage et clés par hub (jamais en clair)")
    public InternalIntegrationSettingsDto settings() {
        return internalIntegrationService.settings(TenantContext.companyId());
    }

    @PostMapping("/enable")
    @Operation(summary = "Activer le partage", description = "Interrupteur général : les clés existantes redeviennent valides")
    public InternalIntegrationSettingsDto enable() {
        internalIntegrationService.enable(TenantContext.companyId());
        return internalIntegrationService.settings(TenantContext.companyId());
    }

    @PostMapping("/disable")
    @Operation(summary = "Désactiver le partage", description = "Toutes les clés sont rejetées, mais conservées pour une réactivation")
    public InternalIntegrationSettingsDto disable() {
        internalIntegrationService.disable(TenantContext.companyId());
        return internalIntegrationService.settings(TenantContext.companyId());
    }

    @PostMapping("/keys/{consumer}")
    @Operation(summary = "Générer ou remplacer la clé d'un hub",
            description = "consumer : COMPLIANCE_ENGINE, LOGISTIX, FINANCE ou PORTAL. Remplace la clé existante "
                    + "(l'ancienne est invalide immédiatement). La clé n'est montrée que dans cette réponse.")
    public GeneratedIntegrationKeyDto generateKey(@PathVariable String consumer) {
        return internalIntegrationService.generateKey(TenantContext.companyId(), consumer);
    }

    @DeleteMapping("/keys/{consumer}")
    @Operation(summary = "Révoquer la clé d'un hub", description = "Y compris la clé historique partagée (LEGACY)")
    public InternalIntegrationSettingsDto revokeKey(@PathVariable String consumer) {
        internalIntegrationService.revokeKey(TenantContext.companyId(), consumer);
        return internalIntegrationService.settings(TenantContext.companyId());
    }

    @GetMapping("/shipment-creation/settings")
    @Operation(summary = "Statut de la création d'expédition externe",
            description = "Opt-in SÉPARÉ de /settings ci-dessus (ADR-0002) : la lecture seule ne suffit pas, voir docs/adr/0002-creation-expedition-externe.md")
    public ExternalShipmentCreationSettingsDto shipmentCreationSettings() {
        return internalIntegrationService.shipmentCreationSettings(TenantContext.companyId());
    }

    @PostMapping("/shipment-creation/enable")
    @Operation(summary = "Activer la création d'expédition externe",
            description = "Autorise en plus compliance-engine à créer des expéditions brouillon (toujours DRAFT) via POST /api/internal/shipments -- sans effet si le partage en lecture (/enable ci-dessus) n'est pas déjà activé")
    public ExternalShipmentCreationSettingsDto enableShipmentCreation() {
        return internalIntegrationService.setShipmentCreationEnabled(TenantContext.companyId(), true);
    }

    @PostMapping("/shipment-creation/disable")
    @Operation(summary = "Désactiver la création d'expédition externe")
    public ExternalShipmentCreationSettingsDto disableShipmentCreation() {
        return internalIntegrationService.setShipmentCreationEnabled(TenantContext.companyId(), false);
    }
}
