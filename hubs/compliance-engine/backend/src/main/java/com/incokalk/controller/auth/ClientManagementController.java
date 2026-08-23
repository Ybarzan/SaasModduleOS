package com.incokalk.controller.auth;

import com.incokalk.model.ClientUser;
import com.incokalk.model.Company;
import com.incokalk.model.CompanyRole;
import com.incokalk.security.RequiresPlan;
import com.incokalk.security.RolesAllowed;
import com.incokalk.service.ClientAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/v1/clients")
@RequiredArgsConstructor
@Tag(name = "Client Management", description = "Gestion des comptes clients (admin)")
@RequiresPlan(Company.Plan.STARTER)
public class ClientManagementController {

    private final ClientAuthService clientAuthService;

    @GetMapping
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Liste des clients")
    public ResponseEntity<List<ClientUser>> listClients(HttpServletRequest req) {
        return ResponseEntity.ok(clientAuthService.listClients(extractCompanyId(req)));
    }

    @PostMapping
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    @Operation(summary = "Créer un compte client")
    public ResponseEntity<ClientUser> createClient(
            @Valid @RequestBody CreateClientReq body, HttpServletRequest req) {
        ClientUser client = clientAuthService.createClient(
            extractCompanyId(req), body.email(), body.password(), body.fullName(), body.phone());
        return ResponseEntity.status(HttpStatus.CREATED).body(client);
    }

    @PutMapping("/{id}")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    @Operation(summary = "Modifier un client")
    public ResponseEntity<ClientUser> updateClient(
            @PathVariable UUID id, @Valid @RequestBody UpdateClientReq body, HttpServletRequest req) {
        ClientUser client = clientAuthService.updateClient(
            id, extractCompanyId(req), body.fullName(), body.phone(), body.active());
        return ResponseEntity.ok(client);
    }

    @PostMapping("/{id}/reset-password")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    @Operation(summary = "Réinitialiser le mot de passe")
    public ResponseEntity<Map<String, String>> resetPassword(
            @PathVariable UUID id, @Valid @RequestBody ResetPasswordReq body, HttpServletRequest req) {
        clientAuthService.resetClientPassword(id, extractCompanyId(req), body.newPassword());
        return ResponseEntity.ok(Map.of("message", "Mot de passe réinitialisé"));
    }

    @DeleteMapping("/{id}")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    @Operation(summary = "Supprimer un client")
    public ResponseEntity<Map<String, String>> deleteClient(
            @PathVariable UUID id, HttpServletRequest req) {
        clientAuthService.deleteClient(id, extractCompanyId(req));
        return ResponseEntity.ok(Map.of("message", "Client supprimé"));
    }

    @GetMapping("/stats")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Statistiques clients")
    public ResponseEntity<Map<String, Object>> clientStats(HttpServletRequest req) {
        return ResponseEntity.ok(clientAuthService.clientStats(extractCompanyId(req)));
    }

    private UUID extractCompanyId(HttpServletRequest req) {
        Object id = req.getAttribute("companyId");
        if (id == null) throw new RuntimeException("Entreprise non trouvée");
        return id instanceof UUID u ? u : UUID.fromString(id.toString());
    }

    record CreateClientReq(@Email @NotBlank String email,
                           @NotBlank @Size(min = 8) String password,
                           @NotBlank String fullName, String phone) {}
    record UpdateClientReq(String fullName, String phone, Boolean active) {}
    record ResetPasswordReq(@NotBlank @Size(min = 8) String newPassword) {}
}
