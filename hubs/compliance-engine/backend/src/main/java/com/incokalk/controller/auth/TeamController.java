package com.incokalk.controller.auth;

import com.incokalk.dto.auth.InviteMemberDTO;
import com.incokalk.model.CompanyRole;
import com.incokalk.model.User;
import com.incokalk.security.RolesAllowed;
import com.incokalk.service.TeamService;
import com.incokalk.tenant.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/team")
@RequiredArgsConstructor
public class TeamController {

    private final TeamService teamService;

    @GetMapping
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER, CompanyRole.Role.USER})
    public ResponseEntity<List<Map<String, Object>>> listMembers(HttpServletRequest req) {
        UUID companyId = extractCompanyId(req);
        List<User> members = teamService.listMembers(companyId);
        return ResponseEntity.ok(members.stream().map(u -> {
            var companyRole = teamService.findUserRole(companyId, u.getId());
            String role = companyRole.map(r -> r.getRole().name()).orElse("USER");
            var customRole = companyRole.map(CompanyRole::getCustomRole).orElse(null);

            Map<String, Object> result = new java.util.HashMap<>();
            result.put("id", u.getId());
            result.put("email", u.getEmail());
            result.put("fullName", u.getFullName() != null ? u.getFullName() : "");
            result.put("role", role);
            result.put("active", u.isActive());
            result.put("createdAt", u.getCreatedAt());
            result.put("customRoleId", customRole != null ? customRole.getId().toString() : null);
            result.put("customRoleName", customRole != null ? customRole.getName() : null);
            return result;
        }).toList());
    }

    @PostMapping
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    public ResponseEntity<Map<String, Object>> inviteMember(
            @Valid @RequestBody InviteMemberDTO dto,
            HttpServletRequest req) {
        UUID companyId = extractCompanyId(req);
        UUID actingUserId = extractUserId(req);
        CompanyRole.Role role = dto.getRole() != null
            ? CompanyRole.Role.valueOf(dto.getRole().toUpperCase())
            : CompanyRole.Role.USER;
        User user = teamService.inviteMember(dto.getEmail(), dto.getFullName(), role, companyId, actingUserId);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.<String, Object>of(
            "id", user.getId(),
            "email", user.getEmail(),
            "role", role.name(),
            "message", "Membre invité avec succès"
        ));
    }

    @PutMapping("/{userId}")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    public ResponseEntity<Map<String, Object>> updateMember(
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateMemberReq body,
            HttpServletRequest req) {
        UUID companyId = extractCompanyId(req);
        UUID actingUserId = extractUserId(req);
        String fullName = body.fullName();
        CompanyRole.Role role = body.role() != null
            ? CompanyRole.Role.valueOf(body.role().toUpperCase())
            : null;
        teamService.updateMember(userId, fullName, role, body.customRoleId(), companyId, actingUserId);
        String currentRole = teamService.findUserRole(companyId, userId)
            .map(r -> r.getRole().name())
            .orElse("USER");
        return ResponseEntity.ok(Map.<String, Object>of(
            "id", userId,
            "role", currentRole,
            "message", "Membre mis à jour"
        ));
    }

    @DeleteMapping("/{userId}")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    public ResponseEntity<Map<String, String>> removeMember(
            @PathVariable UUID userId,
            HttpServletRequest req) {
        UUID companyId = extractCompanyId(req);
        teamService.removeMember(userId, companyId);
        return ResponseEntity.ok(Map.of("message", "Membre retiré de l'équipe"));
    }

    @GetMapping("/stats")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER, CompanyRole.Role.USER})
    public ResponseEntity<Map<String, Object>> teamStats(HttpServletRequest req) {
        UUID companyId = extractCompanyId(req);
        List<User> members = teamService.listMembers(companyId);
        List<CompanyRole> roles = teamService.findRoles(companyId);
        long owners = roles.stream().filter(r -> r.getRole() == CompanyRole.Role.OWNER).count();
        long admins = roles.stream().filter(r -> r.getRole() == CompanyRole.Role.ADMIN).count();
        long managers = roles.stream().filter(r -> r.getRole() == CompanyRole.Role.MANAGER).count();
        long users = roles.stream().filter(r -> r.getRole() == CompanyRole.Role.USER).count();
        return ResponseEntity.ok(Map.<String, Object>of(
            "total", members.size(),
            "owners", owners,
            "admins", admins,
            "managers", managers,
            "users", users
        ));
    }

    private UUID extractUserId(HttpServletRequest req) {
        Object id = req.getAttribute("userId");
        if (id == null) throw new RuntimeException("Non authentifié");
        return id instanceof UUID u ? u : UUID.fromString(id.toString());
    }

    private UUID extractCompanyId(HttpServletRequest req) {
        UUID companyId = TenantContext.get();
        if (companyId == null) {
            Object id = req.getAttribute("companyId");
            if (id != null) {
                companyId = id instanceof UUID u ? u : UUID.fromString(id.toString());
            }
        }
        if (companyId == null) throw new RuntimeException("Company ID non disponible");
        return companyId;
    }

    public record UpdateMemberReq(String fullName, String role, String customRoleId) {}
}
