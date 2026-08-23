package com.fleethub.controller;

import com.fleethub.dto.AuditLogDto;
import com.fleethub.dto.DeleteAccountRequest;
import com.fleethub.security.AppUserPrincipal;
import com.fleethub.security.TenantContext;
import com.fleethub.service.AccountService;
import com.fleethub.service.AuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * Droits RGPD du tenant : export des données (portabilité, art. 20), journal
 * d'audit, et suppression définitive du compte (effacement, art. 17). Routes
 * réservées aux ADMIN (cf. vérifications dans les méthodes).
 */
@RestController
@RequestMapping("/api/account")
@RequiredArgsConstructor
@Tag(name = "Compte utilisateur", description = "Gestion du compte societe et droits RGPD (export, audit, suppression)")
public class AccountController {

    private final AccountService accountService;
    private final AuditService auditService;

    @GetMapping("/export")
    @Operation(summary = "Exporter les données", description = "Exporte toutes les données de la société au format JSON (portabilité RGPD)")
    @ApiResponse(responseCode = "200", description = "Export réalisé avec succès")
    @ApiResponse(responseCode = "403", description = "Réservé aux administrateurs")
    public Map<String, Object> export() {
        requireAdmin();
        Long companyId = TenantContext.require().getId();
        auditService.log("EXPORT_DONNEES", "Export RGPD des données du tenant");
        return accountService.exportData(companyId);
    }

    @GetMapping("/audit-log")
    @Operation(summary = "Journal d'audit", description = "Retourne l'historique des actions effectuées sur le compte")
    @ApiResponse(responseCode = "200", description = "Journal retourné avec succès")
    @ApiResponse(responseCode = "403", description = "Réservé aux administrateurs")
    public List<AuditLogDto> auditLog() {
        requireAdmin();
        return accountService.auditLogs(TenantContext.require().getId());
    }

    @PostMapping("/delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Supprimer le compte", description = "Supprime définitivement le compte societe et toutes ses données (effacement RGPD)")
    @ApiResponse(responseCode = "204", description = "Compte supprimé avec succès")
    @ApiResponse(responseCode = "403", description = "Réservé aux administrateurs")
    @ApiResponse(responseCode = "401", description = "Mot de passe incorrect")
    public void delete(@Valid @RequestBody DeleteAccountRequest request) {
        requireAdmin();
        AppUserPrincipal principal = principal();
        Long companyId = TenantContext.require().getId();
        auditService.log("SUPPRESSION_COMPTE", "Suppression RGPD de la société " + companyId);
        accountService.deleteAccount(companyId, principal.getUsername(), request.password());
    }

    private void requireAdmin() {
        if (!"ADMIN".equals(principal().getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Seul un ADMIN peut gérer les données RGPD de la société");
        }
    }

    private AppUserPrincipal principal() {
        Object p = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(p instanceof AppUserPrincipal appUserPrincipal)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Utilisateur non authentifié");
        }
        return appUserPrincipal;
    }
}
