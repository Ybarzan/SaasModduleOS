package com.incokalk.controller.auth;

import com.incokalk.model.ApiKey;
import com.incokalk.model.CompanyRole;
import com.incokalk.model.User;
import com.incokalk.security.RolesAllowed;
import com.incokalk.service.ApiKeyService;
import com.incokalk.tenant.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/api-keys")
@RequiredArgsConstructor
@Tag(name = "API Keys", description = "Gestion des clés API")
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    @PostMapping
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    @Operation(summary = "Créer une clé API",
               security = @SecurityRequirement(name = "BearerAuth"))
    public ResponseEntity<Map<String, Object>> create(
            @Valid @RequestBody CreateApiKeyReq body, HttpServletRequest req) {
        UUID userId = extractUserId(req);
        UUID companyId = TenantContext.get();
        String planStr = body.plan() != null ? body.plan().toUpperCase() : "FREE";
        User.Plan plan;
        try { plan = User.Plan.valueOf(planStr); }
        catch (Exception e) { plan = User.Plan.FREE; }

        var created = apiKeyService.create(userId, companyId, body.name() != null ? body.name() : "Ma clé", plan);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
            "id", created.id(), "key", created.rawKey(),
            "prefix", created.prefix(), "plan", created.plan(),
            "dailyLimit", created.dailyLimit(),
            "warning", "Conservez cette clé : elle ne sera plus affichée."
        ));
    }

    @GetMapping
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    @Operation(summary = "Lister les clés", security = @SecurityRequirement(name = "BearerAuth"))
    public ResponseEntity<List<ApiKey>> list() {
        return ResponseEntity.ok(apiKeyService.listForCompany(TenantContext.get()));
    }

    @DeleteMapping("/{id}")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    @Operation(summary = "Révoquer une clé", security = @SecurityRequirement(name = "BearerAuth"))
    public ResponseEntity<Map<String, String>> revoke(@PathVariable UUID id) {
        apiKeyService.revoke(id, TenantContext.get());
        return ResponseEntity.ok(Map.of("message", "Clé révoquée"));
    }

    private UUID extractUserId(HttpServletRequest req) {
        Object id = req.getAttribute("userId");
        if (id == null) throw new RuntimeException("Non authentifié");
        return id instanceof UUID u ? u : UUID.fromString(id.toString());
    }

    public record CreateApiKeyReq(@Size(min = 1, max = 100) String name, String plan) {}
}
