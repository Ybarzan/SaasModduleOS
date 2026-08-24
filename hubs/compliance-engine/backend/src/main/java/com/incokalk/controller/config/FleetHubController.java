package com.incokalk.controller.config;

import com.incokalk.dto.config.FleetHubConfigDTO;
import com.incokalk.model.CompanyRole;
import com.incokalk.model.FleetHubConfig;
import com.incokalk.security.RolesAllowed;
import com.incokalk.service.fleethub.FleetHubConfigService;
import com.incokalk.tenant.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Configuration de l'intégration fleet-hub (docs/07-integration-fleet-hub.md) :
 * identifiants d'un compte de service fleet-hub, utilisés pour lire la position
 * GPS de la flotte propre du client via son API REST existante. fleet-hub reste
 * un service indépendant -- jamais d'accès direct à sa base de données.
 */
@RestController
@RequestMapping("/v1/fleethub")
@RequiredArgsConstructor
@Tag(name = "Fleet Hub Integration", description = "Intégration avec fleet-hub (flotte propre du client)")
public class FleetHubController {

    private final FleetHubConfigService configService;

    @GetMapping
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Lister les configurations fleet-hub")
    public ResponseEntity<List<FleetHubConfig>> listConfigs() {
        return ResponseEntity.ok(configService.listConfigs(TenantContext.get()));
    }

    @PostMapping
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    @Operation(summary = "Créer une configuration fleet-hub")
    public ResponseEntity<FleetHubConfig> createConfig(@Valid @RequestBody FleetHubConfigDTO dto) {
        return ResponseEntity.ok(configService.createConfig(dto, TenantContext.get()));
    }

    @PutMapping("/{id}")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    @Operation(summary = "Mettre à jour une configuration fleet-hub")
    public ResponseEntity<FleetHubConfig> updateConfig(@PathVariable UUID id, @Valid @RequestBody FleetHubConfigDTO dto) {
        return ResponseEntity.ok(configService.updateConfig(id, dto, TenantContext.get()));
    }

    @DeleteMapping("/{id}")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    @Operation(summary = "Supprimer une configuration fleet-hub")
    public ResponseEntity<Void> deleteConfig(@PathVariable UUID id) {
        configService.deleteConfig(id, TenantContext.get());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/test")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Tester la connexion fleet-hub")
    public ResponseEntity<Map<String, Boolean>> testConnection(@PathVariable UUID id) {
        boolean success = configService.testConnection(id, TenantContext.get());
        return ResponseEntity.ok(Map.of("success", success));
    }
}
