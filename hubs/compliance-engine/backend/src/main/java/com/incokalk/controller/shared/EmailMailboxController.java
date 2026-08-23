package com.incokalk.controller.shared;

import com.incokalk.dto.shared.EmailIntakeLogResponse;
import com.incokalk.dto.shared.EmailMailboxRequest;
import com.incokalk.dto.shared.EmailMailboxResponse;
import com.incokalk.model.Company;
import com.incokalk.model.CompanyRole;
import com.incokalk.security.RequiresPlan;
import com.incokalk.security.RolesAllowed;
import com.incokalk.service.EmailMailboxService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/email-intake")
@RequiredArgsConstructor
@Tag(name = "Email Mailboxes", description = "Configuration des boîtes email surveillées par entreprise")
@RequiresPlan(Company.Plan.STARTER)
public class EmailMailboxController {

    private final EmailMailboxService mailboxService;

    @GetMapping
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    @Operation(summary = "Lister les boîtes email configurées")
    public ResponseEntity<List<EmailMailboxResponse>> list() {
        return ResponseEntity.ok(mailboxService.list());
    }

    @GetMapping("/logs")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    @Operation(summary = "Historique des synchronisations")
    public ResponseEntity<List<EmailIntakeLogResponse>> logs() {
        return ResponseEntity.ok(mailboxService.logs());
    }

    @PostMapping
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    @Operation(summary = "Ajouter une boîte email")
    public ResponseEntity<EmailMailboxResponse> create(@RequestBody EmailMailboxRequest req) {
        return ResponseEntity.ok(mailboxService.create(req));
    }

    @PutMapping("/{id}")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    @Operation(summary = "Modifier une boîte email")
    public ResponseEntity<EmailMailboxResponse> update(@PathVariable UUID id, @RequestBody EmailMailboxRequest req) {
        return ResponseEntity.ok(mailboxService.update(id, req));
    }

    @DeleteMapping("/{id}")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    @Operation(summary = "Supprimer une boîte email")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        mailboxService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/test")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    @Operation(summary = "Tester la connexion à une boîte email")
    public ResponseEntity<EmailMailboxResponse> test(@PathVariable UUID id) {
        return ResponseEntity.ok(mailboxService.testConnection(id));
    }
}
