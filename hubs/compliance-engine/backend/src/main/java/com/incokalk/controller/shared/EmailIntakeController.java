package com.incokalk.controller.shared;

import com.incokalk.model.CompanyRole;
import com.incokalk.model.EmailIntake;
import com.incokalk.security.RolesAllowed;
import com.incokalk.service.EmailIntakeService;
import com.incokalk.tenant.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/email-intake/messages")
@RequiredArgsConstructor
@Tag(name = "Email Intake", description = "Parsing automatique des emails de demande de devis")
public class EmailIntakeController {

    private final EmailIntakeService emailIntakeService;

    @GetMapping
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Historique des emails reçus")
    public ResponseEntity<List<EmailIntake>> getHistory() {
        UUID companyId = TenantContext.get();
        return ResponseEntity.ok(emailIntakeService.getIntakeHistory(companyId));
    }

    @GetMapping("/pending")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    @Operation(summary = "Emails en attente de traitement")
    public ResponseEntity<List<EmailIntake>> getPending() {
        return ResponseEntity.ok(emailIntakeService.getPendingIntakes());
    }

    @GetMapping("/{id}")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Détail d un email reçu")
    public ResponseEntity<?> getIntake(@PathVariable UUID id) {
        EmailIntake intake = emailIntakeService.getIntake(id);
        if (intake == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(intake);
    }

    @PostMapping("/{id}/confirm")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    @Operation(summary = "Confirmer un email et créer un brouillon d expédition")
    public ResponseEntity<?> confirmIntake(@PathVariable UUID id) {
        UUID companyId = TenantContext.get();
        EmailIntake intake = emailIntakeService.confirmIntake(id, companyId);
        if (intake == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(intake);
    }

    @PostMapping("/{id}/reject")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    @Operation(summary = "Rejeter un email")
    public ResponseEntity<Void> rejectIntake(@PathVariable UUID id) {
        emailIntakeService.rejectIntake(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/stats")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    @Operation(summary = "Statistiques du système d email intake")
    public ResponseEntity<Map<String, Object>> getStats() {
        return ResponseEntity.ok(emailIntakeService.getStats());
    }
}
