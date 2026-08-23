package com.incokalk.controller.auth;

import com.incokalk.dto.auth.CustomRoleRequest;
import com.incokalk.dto.auth.RoleResponse;
import com.incokalk.model.CompanyRole;
import com.incokalk.security.RolesAllowed;
import com.incokalk.service.CustomRoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/roles")
@RequiredArgsConstructor
@Tag(name = "Custom Roles", description = "Rôles personnalisés avec permissions granulaires par entreprise")
public class CustomRoleController {

    private final CustomRoleService customRoleService;

    @GetMapping
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Lister les rôles (système + personnalisés)")
    public ResponseEntity<List<RoleResponse>> list() {
        return ResponseEntity.ok(customRoleService.list());
    }

    @PostMapping
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    @Operation(summary = "Créer un rôle personnalisé")
    public ResponseEntity<RoleResponse> create(@Valid @RequestBody CustomRoleRequest req) {
        return ResponseEntity.ok(customRoleService.create(req));
    }

    @PutMapping("/{id}")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    @Operation(summary = "Modifier un rôle personnalisé")
    public ResponseEntity<RoleResponse> update(@PathVariable UUID id, @Valid @RequestBody CustomRoleRequest req) {
        return ResponseEntity.ok(customRoleService.update(id, req));
    }

    @DeleteMapping("/{id}")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    @Operation(summary = "Supprimer un rôle personnalisé")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        customRoleService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
