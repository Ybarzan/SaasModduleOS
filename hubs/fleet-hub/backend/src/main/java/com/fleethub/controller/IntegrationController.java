package com.fleethub.controller;

import com.fleethub.dto.IntegrationConfigDto;
import com.fleethub.dto.IntegrationConfigRequest;
import com.fleethub.dto.IntegrationTestResultDto;
import com.fleethub.dto.ProviderMetaDto;
import com.fleethub.service.IntegrationService;
import com.fleethub.security.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Configurations d'intégration self-service de la société courante (ADMIN du
 * tenant uniquement) : lister, créer/éditer/supprimer une intégration
 * (GPS, tachygraphe, carburant, DHL…), tester la connexion et récupérer la
 * clé/URL de webhook à transmettre au fournisseur.
 */
@RestController
@RequestMapping("/api/integrations")
@RequiredArgsConstructor
@Tag(name = "Intégrations", description = "Configuration self-service des intégrations externes (GPS, tachygraphe, carburant)")
public class IntegrationController {

    private final IntegrationService service;

    @GetMapping
    @Operation(summary = "Lister les intégrations", description = "Retourne les configurations d'intégration de la société")
    public List<IntegrationConfigDto> list() {
        return service.list(TenantContext.require().getId());
    }

    @GetMapping("/providers")
    @Operation(summary = "Fournisseurs disponibles", description = "Liste des fournisseurs d'intégration supportés")
    public List<ProviderMetaDto> providers() {
        return service.providers();
    }

    @PostMapping
    @Operation(summary = "Créer une intégration", description = "Ajoute une nouvelle configuration d'intégration")
    @ResponseStatus(HttpStatus.CREATED)
    public IntegrationConfigDto create(@Valid @RequestBody IntegrationConfigRequest request) {
        return service.create(TenantContext.require().getId(), request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Modifier une intégration", description = "Met à jour la configuration d'une intégration existante")
    public IntegrationConfigDto update(@PathVariable Long id,
                                       @Valid @RequestBody IntegrationConfigRequest request) {
        return service.update(TenantContext.require().getId(), id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Supprimer une intégration", description = "Supprime une configuration d'intégration")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(TenantContext.require().getId(), id);
    }

    /** Teste une configuration déjà enregistrée (clé stockée). */
    @PostMapping("/{id}/test")
    @Operation(summary = "Tester une intégration", description = "Teste la connexion avec les paramètres enregistrés")
    public IntegrationTestResultDto testExisting(@PathVariable Long id) {
        return service.testConnection(TenantContext.require().getId(), id);
    }

    /** Teste une configuration en cours de saisie (avant enregistrement). */
    @PostMapping("/test")
    @Operation(summary = "Tester un brouillon", description = "Teste la connexion avant sauvegarde de la configuration")
    public IntegrationTestResultDto testDraft(@Valid @RequestBody IntegrationConfigRequest request) {
        return service.testConnection(request);
    }
}
