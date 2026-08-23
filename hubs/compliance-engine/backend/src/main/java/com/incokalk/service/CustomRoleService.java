package com.incokalk.service;

import com.incokalk.dto.auth.CustomRoleRequest;
import com.incokalk.dto.auth.RoleResponse;
import com.incokalk.exception.ResourceNotFoundException;
import com.incokalk.model.CompanyRole;
import com.incokalk.model.CustomRole;
import com.incokalk.repository.CompanyRepository;
import com.incokalk.repository.CompanyRoleRepository;
import com.incokalk.repository.CustomRoleRepository;
import com.incokalk.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomRoleService {

    private final CustomRoleRepository customRoleRepo;
    private final CompanyRoleRepository companyRoleRepo;
    private final CompanyRepository companyRepo;
    private final HubUpsellSignalService hubUpsellSignalService;

    private static final List<String> MODULES = List.of(
            "shipments", "carriers", "finance", "admin", "quotes", "analytics", "notifications", "team", "settings");
    private static final List<String> ACTIONS = List.of("view", "create", "edit", "delete");

    private static List<String> allPermissions() {
        List<String> perms = new ArrayList<>();
        for (String module : MODULES) {
            for (String action : ACTIONS) {
                perms.add(module + ":" + action);
            }
        }
        return perms;
    }

    private static List<String> permissionsFor(CompanyRole.Role role) {
        return switch (role) {
            case OWNER, ADMIN -> allPermissions();
            case MANAGER -> allPermissions().stream()
                    .filter(p -> !p.endsWith(":delete"))
                    .filter(p -> !p.startsWith("admin:") && !p.startsWith("team:") && !p.startsWith("settings:"))
                    .toList();
            case USER -> allPermissions().stream()
                    .filter(p -> p.endsWith(":view"))
                    .filter(p -> !p.startsWith("admin:") && !p.startsWith("team:") && !p.startsWith("settings:"))
                    .toList();
        };
    }

    private static final Map<CompanyRole.Role, String> SYSTEM_ROLE_LABELS = Map.of(
            CompanyRole.Role.OWNER, "Propriétaire",
            CompanyRole.Role.ADMIN, "Administrateur",
            CompanyRole.Role.MANAGER, "Manager",
            CompanyRole.Role.USER, "Utilisateur"
    );

    private static final Map<CompanyRole.Role, String> SYSTEM_ROLE_DESCRIPTIONS = Map.of(
            CompanyRole.Role.OWNER, "Accès complet, ne peut pas être supprimé ni modifié",
            CompanyRole.Role.ADMIN, "Accès complet à la gestion quotidienne",
            CompanyRole.Role.MANAGER, "Gestion opérationnelle sans droits d'administration",
            CompanyRole.Role.USER, "Accès en lecture aux modules métier"
    );

    @Transactional(readOnly = true)
    public List<String> effectivePermissions(UUID userId, UUID companyId) {
        CompanyRole companyRole = companyRoleRepo.findByCompanyIdAndUserId(companyId, userId).orElse(null);
        if (companyRole == null) return List.of();

        Set<String> permissions = new LinkedHashSet<>(permissionsFor(companyRole.getRole()));
        if (companyRole.getCustomRole() != null) {
            permissions.addAll(splitPermissions(companyRole.getCustomRole().getPermissions()));
        }
        return List.copyOf(permissions);
    }

    @Transactional(readOnly = true)
    public List<RoleResponse> list() {
        UUID companyId = TenantContext.get();
        List<RoleResponse> result = new ArrayList<>();

        for (CompanyRole.Role role : CompanyRole.Role.values()) {
            long count = companyRoleRepo.countByCompanyIdAndRole(companyId, role);
            result.add(new RoleResponse(
                    role.name(), SYSTEM_ROLE_LABELS.get(role), SYSTEM_ROLE_DESCRIPTIONS.get(role),
                    count, permissionsFor(role), true));
        }

        for (CustomRole cr : customRoleRepo.findByCompanyIdOrderByCreatedAtDesc(companyId)) {
            long count = companyRoleRepo.countByCustomRole_Id(cr.getId());
            result.add(new RoleResponse(
                    cr.getId().toString(), cr.getName(), cr.getDescription(), count,
                    splitPermissions(cr.getPermissions()), false));
        }

        return result;
    }

    @Transactional
    public RoleResponse create(CustomRoleRequest req) {
        UUID companyId = TenantContext.get();
        if (req.name() == null || req.name().isBlank()) {
            throw new IllegalArgumentException("Le nom du rôle est obligatoire");
        }
        if (customRoleRepo.existsByCompanyIdAndNameIgnoreCase(companyId, req.name())) {
            throw new IllegalArgumentException("Un rôle nommé '" + req.name() + "' existe déjà");
        }

        CustomRole role = CustomRole.builder()
                .company(companyRepo.getReferenceById(companyId))
                .name(req.name())
                .description(req.description())
                .permissions(joinPermissions(req.permissions()))
                .build();

        customRoleRepo.save(role);
        try {
            hubUpsellSignalService.onCustomRoleCreated(role);
        } catch (Exception e) {
            // Le signal d'upsell ne doit jamais faire echouer la creation du role.
            log.warn("Echec du signal d'upsell pour le role '{}': {}", role.getName(), e.getMessage());
        }
        return new RoleResponse(role.getId().toString(), role.getName(), role.getDescription(), 0,
                splitPermissions(role.getPermissions()), false);
    }

    @Transactional
    public RoleResponse update(UUID id, CustomRoleRequest req) {
        UUID companyId = TenantContext.get();
        CustomRole role = customRoleRepo.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Rôle non trouvé"));

        if (req.name() != null && !req.name().isBlank()) {
            if (!req.name().equalsIgnoreCase(role.getName())
                    && customRoleRepo.existsByCompanyIdAndNameIgnoreCase(companyId, req.name())) {
                throw new IllegalArgumentException("Un rôle nommé '" + req.name() + "' existe déjà");
            }
            role.setName(req.name());
        }
        if (req.description() != null) role.setDescription(req.description());
        if (req.permissions() != null) role.setPermissions(joinPermissions(req.permissions()));

        customRoleRepo.save(role);
        long count = companyRoleRepo.countByCustomRole_Id(role.getId());
        return new RoleResponse(role.getId().toString(), role.getName(), role.getDescription(), count,
                splitPermissions(role.getPermissions()), false);
    }

    @Transactional
    public void delete(UUID id) {
        UUID companyId = TenantContext.get();
        CustomRole role = customRoleRepo.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Rôle non trouvé"));

        long assigned = companyRoleRepo.countByCustomRole_Id(id);
        if (assigned > 0) {
            throw new IllegalStateException(
                    "Impossible de supprimer ce rôle : " + assigned + " utilisateur(s) y sont rattaché(s). Réaffectez-les d'abord.");
        }

        customRoleRepo.delete(role);
    }

    private static List<String> splitPermissions(String stored) {
        if (stored == null || stored.isBlank()) return List.of();
        return Arrays.stream(stored.split(",")).filter(s -> !s.isBlank()).toList();
    }

    private static String joinPermissions(List<String> permissions) {
        if (permissions == null) return "";
        return String.join(",", permissions);
    }
}
