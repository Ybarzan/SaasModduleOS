package com.finance.controller;

import com.finance.model.FintechConnection;
import com.finance.security.TenantContext;
import com.finance.service.fintech.FintechSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/fintech")
@RequiredArgsConstructor
@Tag(name = "Fintech", description = "Intégrations bancaires et fintech (Qonto, Spendesk)")
public class FintechController {

    private final FintechSyncService fintechSyncService;

    @GetMapping("/connections")
    @Operation(summary = "Lister les connexions fintech de l'entreprise")
    public ResponseEntity<List<FintechConnection>> listConnections() {
        return ResponseEntity.ok(fintechSyncService.listConnections(TenantContext.get()));
    }

    @PostMapping("/connections")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Créer une connexion fintech (Qonto, Spendesk)")
    public ResponseEntity<FintechConnection> createConnection(@Valid @RequestBody CreateConnectionRequest body) {
        return ResponseEntity.ok(fintechSyncService.createConnection(
            TenantContext.get(), body.getProvider().name(), body.getName(),
            body.getApiKey(), body.getApiSecret()));
    }

    @PutMapping("/connections/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Modifier une connexion fintech")
    public ResponseEntity<FintechConnection> updateConnection(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateConnectionRequest body) {
        return ResponseEntity.ok(fintechSyncService.updateConnection(
            id, TenantContext.get(), body.getName(), body.getApiKey(),
            body.getApiSecret(), body.getActive()));
    }

    @DeleteMapping("/connections/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Supprimer une connexion fintech")
    public ResponseEntity<Void> deleteConnection(@PathVariable UUID id) {
        fintechSyncService.deleteConnection(id, TenantContext.get());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/connections/{id}/test")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Tester une connexion fintech")
    public ResponseEntity<Map<String, Object>> testConnection(@PathVariable UUID id) {
        return ResponseEntity.ok(fintechSyncService.testConnection(id, TenantContext.get()));
    }

    @PostMapping("/connections/{id}/sync")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Synchroniser une connexion fintech")
    public ResponseEntity<Map<String, Object>> syncConnection(@PathVariable UUID id) {
        return ResponseEntity.ok(fintechSyncService.syncConnection(id, TenantContext.get()));
    }

    @GetMapping("/connections/{id}/data")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Récupérer les données synchronisées (comptes, transactions, dépenses)")
    public ResponseEntity<Map<String, Object>> fetchData(@PathVariable UUID id) {
        return ResponseEntity.ok(fintechSyncService.fetchData(id, TenantContext.get()));
    }

    @Data
    public static class CreateConnectionRequest {
        @NotNull
        private FintechConnection.Provider provider;
        @NotBlank
        private String name;
        private String apiKey;
        private String apiSecret;
    }

    @Data
    public static class UpdateConnectionRequest {
        private String name;
        private String apiKey;
        private String apiSecret;
        private Boolean active;
    }
}
