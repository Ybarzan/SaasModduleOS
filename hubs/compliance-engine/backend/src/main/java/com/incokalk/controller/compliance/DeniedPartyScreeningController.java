package com.incokalk.controller.compliance;

import com.incokalk.model.Company;
import com.incokalk.model.CompanyRole;
import com.incokalk.model.DeniedPartyCheck;
import com.incokalk.model.SanctionedEntity;
import com.incokalk.security.RequiresPlan;
import com.incokalk.security.RolesAllowed;
import com.incokalk.service.DeniedPartyScreeningService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/dps")
@RequiredArgsConstructor
@Tag(name = "Denied Party Screening", description = "Vérification contre les listes de sanctions")
@RequiresPlan(Company.Plan.PRO)
public class DeniedPartyScreeningController {

    private final DeniedPartyScreeningService dpsService;

    record ScreenRequest(@NotBlank String name, String countryCode, DeniedPartyCheck.CheckType checkType) {}

    @PostMapping("/screen")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Vérifier un nom contre les listes de sanctions")
    public ResponseEntity<DeniedPartyCheck> screen(@Valid @RequestBody ScreenRequest req, HttpServletRequest httpReq) {
        UUID userId = (UUID) httpReq.getAttribute("userId");
        DeniedPartyCheck.CheckType type = req.checkType() != null ? req.checkType() : DeniedPartyCheck.CheckType.ENTITY;
        return ResponseEntity.ok(dpsService.screen(req.name(), req.countryCode(), type, userId));
    }

    @GetMapping("/history")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Historique des vérifications")
    public ResponseEntity<List<DeniedPartyCheck>> history() {
        return ResponseEntity.ok(dpsService.getHistory());
    }

    @GetMapping("/{id}")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Détail d'une vérification")
    public ResponseEntity<DeniedPartyCheck> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(dpsService.getById(id));
    }

    @GetMapping("/stats")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Statistiques des vérifications")
    public ResponseEntity<Map<String, Object>> stats() {
        return ResponseEntity.ok(dpsService.getStats());
    }

    @GetMapping("/sanctioned-entities")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Lister les entités sanctionnées")
    public ResponseEntity<List<SanctionedEntity>> sanctionedEntities() {
        return ResponseEntity.ok(dpsService.getSanctionedEntities());
    }

    @GetMapping("/alerts")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Alertes DPS — correspondances HIGH/CRITICAL")
    public ResponseEntity<List<DeniedPartyCheck>> alerts() {
        return ResponseEntity.ok(dpsService.getAlerts());
    }
}
