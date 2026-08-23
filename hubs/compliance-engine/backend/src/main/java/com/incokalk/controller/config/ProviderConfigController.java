package com.incokalk.controller.config;

import com.incokalk.dto.config.ProviderConfigDTO;
import com.incokalk.dto.config.ProviderHealthDTO;
import com.incokalk.dto.shipment.QuoteRequestDTO;
import com.incokalk.dto.shipment.QuoteResponseDTO;
import com.incokalk.model.CompanyRole;
import com.incokalk.model.ProviderConfig;
import com.incokalk.security.RolesAllowed;
import com.incokalk.exception.ResourceNotFoundException;
import com.incokalk.service.ProviderHealthService;
import com.incokalk.service.provider.CarrierProvider;
import com.incokalk.service.provider.CarrierProviderRegistry;
import com.incokalk.tenant.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/v1/providers")
@RequiredArgsConstructor
@Tag(name = "Provider Config", description = "Configuration des fournisseurs de tarifs externes")
public class ProviderConfigController {

    private final ProviderHealthService healthService;
    private final CarrierProviderRegistry providerRegistry;

    @GetMapping
    @Operation(summary = "Lister les configurations de fournisseurs")
    public ResponseEntity<List<ProviderConfig>> listProviders() {
        UUID companyId = TenantContext.get();
        return ResponseEntity.ok(healthService.listProviderConfigs(companyId));
    }

    @PostMapping
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Créer ou mettre à jour une configuration de fournisseur")
    public ResponseEntity<ProviderConfig> createOrUpdateProvider(@Valid @RequestBody ProviderConfigDTO dto) {
        UUID companyId = TenantContext.get();

        return ResponseEntity.ok(healthService.createOrUpdateProviderConfig(dto, companyId));
    }

    @DeleteMapping("/{id}")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    @Operation(summary = "Supprimer une configuration de fournisseur")
    public ResponseEntity<Void> deleteProvider(@PathVariable UUID id) {
        UUID companyId = TenantContext.get();
        healthService.deleteProviderConfig(id, companyId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/health")
    @Operation(summary = "Obtenir l'état de santé de tous les fournisseurs")
    public ResponseEntity<List<ProviderHealthDTO>> getHealth() {
        UUID companyId = TenantContext.get();
        return ResponseEntity.ok(healthService.getHealth(companyId));
    }

    @PostMapping("/{id}/test")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Tester la connexion d'un fournisseur")
    public ResponseEntity<List<QuoteResponseDTO>> testProvider(@PathVariable UUID id) {
        UUID companyId = TenantContext.get();

        ProviderConfig config = healthService.findProviderConfigById(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Configuration non trouvée"));

        CarrierProvider provider = providerRegistry.getProvider(config.getProviderType(), companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Fournisseur non disponible: " + config.getProviderType()));

        QuoteRequestDTO testRequest = QuoteRequestDTO.builder()
                .originCountry("FR")
                .destinationCountry("DE")
                .weightKg(1.0)
                .volumeM3(0.01)
                .build();

        try {
            List<QuoteResponseDTO> rates = provider.getRates(testRequest, companyId);
            healthService.recordSuccess(config.getProviderType(), companyId);
            config.setLastHealthCheck(LocalDateTime.now());
            config.setHealthStatus("HEALTHY");
            healthService.createOrUpdateProviderConfig(
                ProviderConfigDTO.builder()
                    .providerType(config.getProviderType())
                    .isActive(config.isActive())
                    .priority(config.getPriority())
                    .configJson(config.getConfigJson())
                    .build(),
                companyId);
            return ResponseEntity.ok(rates);
        } catch (Exception e) {
            healthService.recordFailure(config.getProviderType(), companyId);
            config.setLastHealthCheck(LocalDateTime.now());
            config.setHealthStatus("DEGRADED");
            healthService.createOrUpdateProviderConfig(
                ProviderConfigDTO.builder()
                    .providerType(config.getProviderType())
                    .isActive(config.isActive())
                    .priority(config.getPriority())
                    .configJson(config.getConfigJson())
                    .build(),
                companyId);
            log.error("[ProviderTest] Échec du test pour {}: {}", config.getProviderType(), e.getMessage());
            return ResponseEntity.ok(List.of());
        }
    }
}
