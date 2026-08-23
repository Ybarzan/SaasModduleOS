package com.incokalk.controller.config;

import com.incokalk.model.CompanyBranch;
import com.incokalk.model.CompanyRole;
import com.incokalk.model.InterBranchTransfer;
import com.incokalk.security.RolesAllowed;
import com.incokalk.service.MultiBranchService;
import com.incokalk.tenant.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/branches")
@RequiredArgsConstructor
@Tag(name = "Multi-Branch", description = "Gestion multi-filiales et consolidation")
public class MultiBranchController {

    private final MultiBranchService branchService;

    @GetMapping
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    @Operation(summary = "Liste des filiales actives de la société courante")
    public ResponseEntity<List<CompanyBranch>> getBranches() {
        return ResponseEntity.ok(branchService.getBranches());
    }

    @PostMapping("/add")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    @Operation(summary = "Ajouter une filiale")
    public ResponseEntity<CompanyBranch> addBranch(@Valid @RequestBody AddBranchReq req) {
        UUID companyId = TenantContext.get();
        CompanyBranch branch = branchService.addBranch(companyId, req.branchCompanyId(), req.branchName());
        return ResponseEntity.ok(branch);
    }

    @DeleteMapping("/{id}")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    @Operation(summary = "Désactiver une filiale")
    public ResponseEntity<Void> removeBranch(@PathVariable UUID id) {
        UUID companyId = TenantContext.get();
        branchService.removeBranch(companyId, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/parent")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    @Operation(summary = "Retourne la société mère si la société courante est une filiale")
    public ResponseEntity<?> getParentCompany() {
        return branchService.getParentCompany()
                .map(b -> ResponseEntity.ok((Object) b))
                .orElse(ResponseEntity.ok(Map.of("message", "Cette société n'est pas une filiale")));
    }

    @GetMapping("/consolidated-report")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    @Operation(summary = "Rapport consolidé (expéditions, finances, carbone)")
    public ResponseEntity<Map<String, Object>> getConsolidatedReport() {
        return ResponseEntity.ok(branchService.getConsolidatedReport());
    }

    @GetMapping("/transfers")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    @Operation(summary = "Historique des transferts inter-filiales")
    public ResponseEntity<List<InterBranchTransfer>> getTransferHistory() {
        return ResponseEntity.ok(branchService.getTransferHistory());
    }

    @PostMapping("/transfers")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    @Operation(summary = "Créer un transfert inter-filiales")
    public ResponseEntity<InterBranchTransfer> createTransfer(@Valid @RequestBody CreateTransferReq req) {
        UUID companyId = TenantContext.get();
        InterBranchTransfer transfer = branchService.transferGoods(
                companyId, req.fromBranchId(), req.toBranchId(), req.goodsDescription(), req.quantity());
        return ResponseEntity.ok(transfer);
    }

    public record AddBranchReq(
            @NotNull UUID branchCompanyId,
            @NotBlank String branchName) {}

    public record CreateTransferReq(
            @NotNull UUID fromBranchId,
            @NotNull UUID toBranchId,
            String goodsDescription,
            @NotNull BigDecimal quantity) {}
}
