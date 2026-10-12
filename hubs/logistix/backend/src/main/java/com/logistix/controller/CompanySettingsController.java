package com.logistix.controller;

import com.logistix.service.CompanySettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/company")
@RequiredArgsConstructor
@Tag(name = "Company settings", description = "Paramètres de la société (intégrations)")
public class CompanySettingsController {

    private final CompanySettingsService companySettingsService;

    @GetMapping("/fleethub-key")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Statut de la clé d'intégration fleet-hub", description = "Indique si une clé est enregistrée, jamais la clé elle-même")
    public ResponseEntity<FleetHubKeyStatusRes> getFleetHubKeyStatus() {
        return ResponseEntity.ok(new FleetHubKeyStatusRes(companySettingsService.hasFleetHubApiKey()));
    }

    @PutMapping("/fleethub-key")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Enregistrer la clé d'intégration fleet-hub", description = "Clé générée côté fleet-hub (paramètres > Logistix & Compliance Engine)")
    public ResponseEntity<FleetHubKeyStatusRes> setFleetHubKey(@Valid @RequestBody FleetHubKeyReq req) {
        companySettingsService.setFleetHubApiKey(req.apiKey());
        return ResponseEntity.ok(new FleetHubKeyStatusRes(companySettingsService.hasFleetHubApiKey()));
    }

    @DeleteMapping("/fleethub-key")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Effacer la clé d'intégration fleet-hub")
    public ResponseEntity<FleetHubKeyStatusRes> clearFleetHubKey() {
        companySettingsService.setFleetHubApiKey(null);
        return ResponseEntity.ok(new FleetHubKeyStatusRes(false));
    }

    public record FleetHubKeyReq(@NotBlank String apiKey) {}
    public record FleetHubKeyStatusRes(boolean configured) {}
}
