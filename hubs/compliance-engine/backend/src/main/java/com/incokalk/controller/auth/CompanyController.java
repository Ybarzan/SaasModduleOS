package com.incokalk.controller.auth;

import com.incokalk.dto.auth.CompanyDTO;
import com.incokalk.model.Company;
import com.incokalk.model.CompanyRole;
import com.incokalk.model.User;
import com.incokalk.security.RolesAllowed;
import com.incokalk.service.CompanyService;
import com.incokalk.service.RoleChecker;
import com.incokalk.tenant.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/companies")
@RequiredArgsConstructor
@Tag(name = "Company", description = "Gestion de l'entreprise")
public class CompanyController {

    private final CompanyService companyService;
    private final RoleChecker roleChecker;

    @GetMapping("/me")
    @Operation(summary = "Retourne la company de l'utilisateur courant")
    public ResponseEntity<CompanyDTO> getMyCompany(HttpServletRequest req) {
        extractUserId(req); // ensures the caller is authenticated
        UUID companyId = TenantContext.get();
        if (companyId == null) return ResponseEntity.notFound().build();

        Company company = companyService.findById(companyId).orElse(null);
        if (company == null) return ResponseEntity.notFound().build();

        // Backfill paresseux du code de parrainage pour les sociétés créées avant son
        // introduction (voir CompanyService.getOrCreateReferralCode).
        String referralCode = companyService.getOrCreateReferralCode(companyId);
        return ResponseEntity.ok(toDTO(company, referralCode));
    }

    @PutMapping("/{id}")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    @Operation(summary = "Modifier une company ( OWNER ou ADMIN uniquement)")
    public ResponseEntity<CompanyDTO> updateCompany(
            @PathVariable UUID id,
            @Valid @RequestBody CompanyUpdateReq req,
            HttpServletRequest httpReq) {
        UUID userId = extractUserId(httpReq);
        UUID companyId = TenantContext.get();

        if (!id.equals(companyId)) return ResponseEntity.status(403).build();
        if (!roleChecker.hasRole(userId, companyId, CompanyRole.Role.ADMIN)) {
            return ResponseEntity.status(403).build();
        }

        Company company = companyService.updateCompany(id, req.name(), req.slug(), req.logoUrl());
        return ResponseEntity.ok(toDTO(company));
    }

    @PostMapping("/{id}/invite")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Inviter un utilisateur par email")
    public ResponseEntity<String> inviteUser(
            @PathVariable UUID id,
            @Valid @RequestBody InviteReq req,
            HttpServletRequest httpReq) {
        extractUserId(httpReq); // ensures the caller is authenticated
        UUID companyId = TenantContext.get();

        if (!id.equals(companyId)) return ResponseEntity.status(403).build();

        User user = companyService.findUserByEmail(req.email()).orElse(null);
        if (user == null) {
            return ResponseEntity.ok("Invitation envoyée (email non enregistré — sera créé à la première connexion)");
        }

        CompanyRole existing = companyService.findRoleByCompanyIdAndUserId(companyId, user.getId()).orElse(null);
        if (existing != null) {
            return ResponseEntity.badRequest().body("Utilisateur déjà membre de cette company");
        }

        CompanyRole role = CompanyRole.builder()
                .company(companyService.getReferenceById(companyId))
                .user(user)
                .role(req.role() != null ? req.role() : CompanyRole.Role.USER)
                .build();
        companyService.saveRole(role);

        return ResponseEntity.ok("Utilisateur ajouté avec le rôle " + role.getRole());
    }

    private UUID extractUserId(HttpServletRequest req) {
        Object id = req.getAttribute("userId");
        if (id == null) throw new RuntimeException("Non authentifié");
        return id instanceof UUID u ? u : UUID.fromString(id.toString());
    }

    private CompanyDTO toDTO(Company company) {
        return toDTO(company, company.getReferralCode());
    }

    private CompanyDTO toDTO(Company company, String referralCode) {
        return CompanyDTO.builder()
                .id(company.getId())
                .name(company.getName())
                .slug(company.getSlug())
                .logoUrl(company.getLogoUrl())
                .plan(company.getPlan() != null ? company.getPlan().name() : "FREE")
                .isActive(company.isActive())
                .referralCode(referralCode)
                .build();
    }

    public record CompanyUpdateReq(String name, String slug, String logoUrl) {}
    public record InviteReq(@Email @NotBlank String email, CompanyRole.Role role) {}
}