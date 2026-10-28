package com.incokalk.controller.integration;

import com.incokalk.dto.integration.fleethub.FleetHubWebhookSettingsDto;
import com.incokalk.model.CompanyRole;
import com.incokalk.security.RolesAllowed;
import com.incokalk.service.integration.fleethub.FleetHubWebhookService;
import com.incokalk.tenant.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * Activation opt-in de la réception d'événements d'expédition depuis
 * fleet-hub. Réservé OWNER/ADMIN -- contrairement à
 * "/v1/webhooks/fleethub/**" (réception elle-même), qui est permitAll et
 * s'auto-authentifie via la clé (voir SecurityConfig et
 * FleetHubEventWebhookController).
 */
@RestController
@RequestMapping("/v1/fleethub-webhook")
@RequiredArgsConstructor
@Tag(name = "Webhook fleet-hub", description = "Réception opt-in des événements d'expédition (création, changement de statut) depuis fleet-hub")
public class FleetHubWebhookSettingsController {

    private final FleetHubWebhookService fleetHubWebhookService;

    @GetMapping("/settings")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    @Operation(summary = "Statut de la réception")
    public FleetHubWebhookSettingsDto settings() {
        return fleetHubWebhookService.settings(TenantContext.get());
    }

    @PostMapping("/enable")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    @Operation(summary = "Activer la réception", description = "Active la réception d'événements et (re)génère la clé si absente. La clé n'est montrée que dans cette réponse.")
    public FleetHubWebhookSettingsDto enable() {
        return fleetHubWebhookService.enable(TenantContext.get());
    }

    @PostMapping("/disable")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    @Operation(summary = "Désactiver la réception", description = "Désactive la réception ; la clé existante est effacée, une réactivation future en génère une nouvelle")
    public FleetHubWebhookSettingsDto disable() {
        fleetHubWebhookService.disable(TenantContext.get());
        return fleetHubWebhookService.settings(TenantContext.get());
    }
}
