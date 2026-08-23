package com.incokalk.controller.auth;

import com.incokalk.dto.auth.CompanyRoleDTO;
import com.incokalk.model.CompanyRole;
import com.incokalk.security.RolesAllowed;
import com.incokalk.service.CompanyService;
import com.incokalk.service.RoleChecker;
import com.incokalk.tenant.TenantContext;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/v1/companies/{companyId}/roles")
@RequiredArgsConstructor
@Tag(name = "Roles", description = "Gestion des rôles company")
public class RoleController {

    private final CompanyService companyService;
    private final RoleChecker roleChecker;

    @GetMapping
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Lister les rôles d'une company")
    public ResponseEntity<List<CompanyRoleDTO>> listRoles(
            @PathVariable UUID companyId,
            HttpServletRequest httpReq) {
        UUID userId = extractUserId(httpReq);
        if (!roleChecker.hasRole(userId, companyId, CompanyRole.Role.MANAGER)) {
            return ResponseEntity.status(403).build();
        }
        if (!companyService.existsById(companyId)) {
            return ResponseEntity.notFound().build();
        }

        List<CompanyRoleDTO> roles = companyService.findRolesByCompanyId(companyId).stream()
                .map(r -> {
                    var user = r.getUser();
                    return CompanyRoleDTO.builder()
                            .userId(user.getId())
                            .companyId(companyId)
                            .role(r.getRole())
                            .userEmail(user.getEmail())
                            .userName(user.getFullName())
                            .build();
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(roles);
    }

    @PutMapping("/{targetUserId}")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    @Operation(summary = "Modifier le rôle d'un utilisateur")
    public ResponseEntity<CompanyRoleDTO> updateRole(
            @PathVariable UUID companyId,
            @PathVariable UUID targetUserId,
            @Valid @RequestBody RoleUpdateReq req,
            HttpServletRequest httpReq) {
        UUID userId = extractUserId(httpReq);

        if (!roleChecker.hasRole(userId, companyId, CompanyRole.Role.ADMIN)) {
            return ResponseEntity.status(403).build();
        }

        CompanyRole.Role actingRole = roleChecker.getRole(userId, companyId);
        if (actingRole == null || req.role().ordinal() < actingRole.ordinal()) {
            return ResponseEntity.status(403).build();
        }

        CompanyRole role = companyService.findRoleByCompanyIdAndUserId(companyId, targetUserId).orElse(null);
        if (role == null) return ResponseEntity.notFound().build();

        if (role.getRole() == CompanyRole.Role.OWNER && req.role() != CompanyRole.Role.OWNER) {
            long ownerCount = companyService.findRolesByCompanyId(companyId).stream()
                    .filter(r -> r.getRole() == CompanyRole.Role.OWNER)
                    .count();
            if (ownerCount <= 1) {
                return ResponseEntity.status(409).build();
            }
        }

        role.setRole(req.role());
        companyService.saveRole(role);

        var user = role.getUser();
        return ResponseEntity.ok(CompanyRoleDTO.builder()
                .userId(user.getId())
                .companyId(companyId)
                .role(role.getRole())
                .userEmail(user.getEmail())
                .userName(user.getFullName())
                .build());
    }

    @DeleteMapping("/{targetUserId}")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    @Operation(summary = "Supprimer le rôle d'un utilisateur (le retirer de la company)")
    public ResponseEntity<Void> removeRole(
            @PathVariable UUID companyId,
            @PathVariable UUID targetUserId,
            HttpServletRequest httpReq) {
        UUID userId = extractUserId(httpReq);

        if (!roleChecker.hasRole(userId, companyId, CompanyRole.Role.ADMIN)) {
            return ResponseEntity.status(403).build();
        }

        CompanyRole role = companyService.findRoleByCompanyIdAndUserId(companyId, targetUserId).orElse(null);
        if (role == null) return ResponseEntity.notFound().build();

        if (role.getRole() == CompanyRole.Role.OWNER) {
            return ResponseEntity.badRequest().build();
        }

        companyService.deleteRole(role);
        return ResponseEntity.noContent().build();
    }

    private UUID extractUserId(HttpServletRequest req) {
        Object id = req.getAttribute("userId");
        if (id == null) throw new RuntimeException("Non authentifié");
        return id instanceof UUID u ? u : UUID.fromString(id.toString());
    }

    public record RoleUpdateReq(@jakarta.validation.constraints.NotNull CompanyRole.Role role) {}
}