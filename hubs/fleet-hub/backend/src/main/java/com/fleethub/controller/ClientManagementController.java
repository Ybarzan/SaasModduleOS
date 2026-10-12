package com.fleethub.controller;

import com.fleethub.model.ClientUser;
import com.fleethub.security.TenantContext;
import com.fleethub.service.ClientAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Gestion (staff/admin) des comptes du portail client — porté depuis
 * compliance-engine. Mutations réservées à ADMIN (règle dédiée dans
 * SecurityConfig sur POST/PUT/DELETE /api/clients/**, l'original les
 * réservait à OWNER/ADMIN) ; lecture sous la règle générique ADMIN/GESTIONNAIRE.
 */
@RestController
@RequestMapping("/api/clients")
@RequiredArgsConstructor
@Tag(name = "Client Management", description = "Gestion des comptes clients (admin)")
public class ClientManagementController {

    private final ClientAuthService clientAuthService;

    @GetMapping
    @Operation(summary = "Liste des clients")
    public ResponseEntity<List<ClientUser>> listClients() {
        return ResponseEntity.ok(clientAuthService.listClients(TenantContext.companyId()));
    }

    @PostMapping
    @Operation(summary = "Créer un compte client")
    public ResponseEntity<ClientUser> createClient(@Valid @RequestBody CreateClientReq body) {
        ClientUser client = clientAuthService.createClient(
            TenantContext.companyId(), body.email(), body.password(), body.fullName(), body.phone());
        return ResponseEntity.status(HttpStatus.CREATED).body(client);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Modifier un client")
    public ResponseEntity<ClientUser> updateClient(@PathVariable UUID id, @Valid @RequestBody UpdateClientReq body) {
        ClientUser client = clientAuthService.updateClient(
            id, TenantContext.companyId(), body.fullName(), body.phone(), body.active());
        return ResponseEntity.ok(client);
    }

    @PostMapping("/{id}/reset-password")
    @Operation(summary = "Réinitialiser le mot de passe")
    public ResponseEntity<Map<String, String>> resetPassword(
            @PathVariable UUID id, @Valid @RequestBody ResetPasswordReq body) {
        clientAuthService.resetClientPassword(id, TenantContext.companyId(), body.newPassword());
        return ResponseEntity.ok(Map.of("message", "Mot de passe réinitialisé"));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Supprimer un client")
    public ResponseEntity<Map<String, String>> deleteClient(@PathVariable UUID id) {
        clientAuthService.deleteClient(id, TenantContext.companyId());
        return ResponseEntity.ok(Map.of("message", "Client supprimé"));
    }

    @GetMapping("/stats")
    @Operation(summary = "Statistiques clients")
    public ResponseEntity<Map<String, Object>> clientStats() {
        return ResponseEntity.ok(clientAuthService.clientStats(TenantContext.companyId()));
    }

    record CreateClientReq(@Email @NotBlank String email,
                           @NotBlank @Size(min = 8) String password,
                           @NotBlank String fullName, String phone) {}
    record UpdateClientReq(String fullName, String phone, Boolean active) {}
    record ResetPasswordReq(@NotBlank @Size(min = 8) String newPassword) {}
}
