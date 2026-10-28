package com.fleethub.controller;

import com.fleethub.dto.ComplianceEngineWebhookSettingsDto;
import com.fleethub.dto.ComplianceEngineWebhookUpdateRequest;
import com.fleethub.security.TenantContext;
import com.fleethub.service.ComplianceEngineWebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * Collage de la clé webhook compliance-engine (voir ComplianceEngineWebhookService).
 * Réservé ADMIN (voir SecurityConfig, "/api/compliance-engine-webhook/**" -> ADMIN) --
 * même convention que "/api/internal-integrations/**" (InternalIntegrationController).
 */
@RestController
@RequestMapping("/api/compliance-engine-webhook")
@RequiredArgsConstructor
@Tag(name = "Webhook compliance-engine", description = "Partage d'événements d'expédition vers le moteur de règles de compliance-engine")
public class ComplianceEngineWebhookController {

    private final ComplianceEngineWebhookService complianceEngineWebhookService;

    @GetMapping("/settings")
    @Operation(summary = "Statut de la clé webhook compliance-engine")
    public ComplianceEngineWebhookSettingsDto settings() {
        return complianceEngineWebhookService.settings(TenantContext.companyId());
    }

    @PutMapping("/settings")
    @Operation(summary = "Enregistrer la clé webhook compliance-engine",
            description = "Clé générée côté compliance-engine et collée ici ; jamais réaffichée après enregistrement")
    public ComplianceEngineWebhookSettingsDto update(@Valid @RequestBody ComplianceEngineWebhookUpdateRequest request) {
        return complianceEngineWebhookService.update(TenantContext.companyId(), request.webhookKey());
    }

    @DeleteMapping("/settings")
    @Operation(summary = "Effacer la clé webhook compliance-engine")
    public ComplianceEngineWebhookSettingsDto clear() {
        return complianceEngineWebhookService.clear(TenantContext.companyId());
    }
}
