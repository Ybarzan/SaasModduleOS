package com.fleethub.controller;

import com.fleethub.config.ResourceNotFoundException;
import com.fleethub.dto.AdminCompanyDto;
import com.fleethub.model.AppUser;
import com.fleethub.model.Company;
import com.fleethub.repository.AppUserRepository;
import com.fleethub.repository.CompanyRepository;
import com.fleethub.repository.DriverRepository;
import com.fleethub.repository.TruckRepository;
import com.fleethub.service.email.EmailNotifier;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Back-office de la plateforme SaaS (rôle SAAS_ADMIN) : gestion des sociétés
 * clientes (abonnement, essai, suspension).
 */
@RestController
@RequestMapping("/api/admin/companies")
@RequiredArgsConstructor
@Tag(name = "Administration plateforme", description = "Back-office de gestion des sociétés clientes (SAAS_ADMIN)")
public class AdminController {

    private final CompanyRepository companyRepository;
    private final AppUserRepository userRepository;
    private final DriverRepository driverRepository;
    private final TruckRepository truckRepository;
    private final EmailNotifier emailNotifier;

    @GetMapping
    @Operation(summary = "Lister les sociétés", description = "Retourne la liste de toutes les sociétés clientes de la plateforme")
    @ApiResponse(responseCode = "200", description = "Liste retournée avec succès")
    public List<AdminCompanyDto> all() {
        return companyRepository.findAllByOrderByCreatedAtDesc().stream().map(this::toDto).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Détail d'une société", description = "Retourne les informations détaillées d'une société cliente")
    @ApiResponse(responseCode = "200", description = "Société trouvée")
    @ApiResponse(responseCode = "404", description = "Société introuvable")
    public AdminCompanyDto detail(@PathVariable Long id) {
        return toDto(requireCompany(id));
    }

    @PostMapping("/{id}/suspend")
    @Operation(summary = "Suspendre une société", description = "Suspend l'accès d'une société et notifie ses administrateurs")
    @ApiResponse(responseCode = "200", description = "Société suspendue avec succès")
    @ApiResponse(responseCode = "404", description = "Société introuvable")
    @Transactional
    public AdminCompanyDto suspend(@PathVariable Long id) {
        Company c = requireCompany(id);
        c.setStatus(Company.CompanyStatus.SUSPENDED);
        notifyAdmins(c, emailNotifier::accountSuspended);
        return toDto(companyRepository.save(c));
    }

    @PostMapping("/{id}/activate")
    @Operation(summary = "Activer une société", description = "Réactive l'accès d'une société suspendue et notifie ses administrateurs")
    @ApiResponse(responseCode = "200", description = "Société activée avec succès")
    @ApiResponse(responseCode = "404", description = "Société introuvable")
    @Transactional
    public AdminCompanyDto activate(@PathVariable Long id) {
        Company c = requireCompany(id);
        c.setStatus(Company.CompanyStatus.ACTIVE);
        notifyAdmins(c, emailNotifier::accountActivated);
        return toDto(companyRepository.save(c));
    }

    @PostMapping("/{id}/plan")
    @Operation(summary = "Modifier le plan d'une société", description = "Change la formule d'abonnement d'une société cliente")
    @ApiResponse(responseCode = "200", description = "Plan modifié avec succès")
    @ApiResponse(responseCode = "404", description = "Société introuvable")
    @Transactional
    public AdminCompanyDto setPlan(@PathVariable Long id, @RequestBody PlanRequest body) {
        Company c = requireCompany(id);
        c.setPlan(Company.SubscriptionPlan.valueOf(body.plan()));
        return toDto(companyRepository.save(c));
    }

    @PostMapping("/{id}/extend-trial")
    @Operation(summary = "Prolonger l'essai", description = "Prolonge la période d'essai d'une société du nombre de jours indiqué")
    @ApiResponse(responseCode = "200", description = "Essai prolongé avec succès")
    @ApiResponse(responseCode = "404", description = "Société introuvable")
    @Transactional
    public AdminCompanyDto extendTrial(@PathVariable Long id, @RequestBody ExtendTrialRequest body) {
        Company c = requireCompany(id);
        LocalDateTime base = c.getTrialEndsAt() != null && c.getTrialEndsAt().isAfter(LocalDateTime.now())
                ? c.getTrialEndsAt()
                : LocalDateTime.now();
        c.setTrialEndsAt(base.plusDays(body.days()));
        c.setStatus(Company.CompanyStatus.TRIAL);
        return toDto(companyRepository.save(c));
    }

    private Company requireCompany(Long id) {
        return companyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Société introuvable"));
    }

    private void notifyAdmins(Company c, java.util.function.BiConsumer<String, String> send) {
        userRepository.findByCompanyId(c.getId()).stream()
                .filter(u -> "ADMIN".equals(u.getRole()))
                .filter(AppUser::isEnabled)
                .filter(u -> u.getEmail() != null && !u.getEmail().isBlank())
                .forEach(u -> send.accept(u.getEmail(), c.getName()));
    }

    private AdminCompanyDto toDto(Company c) {
        return AdminCompanyDto.from(c,
                userRepository.countByCompanyId(c.getId()),
                driverRepository.countByCompanyId(c.getId()),
                truckRepository.countByCompanyId(c.getId()));
    }

    public record PlanRequest(@NotNull String plan) {
        public PlanRequest {
            if (plan == null || plan.isBlank()) throw new IllegalArgumentException("Le plan est obligatoire");
            try {
                Company.SubscriptionPlan.valueOf(plan);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Plan invalide. Valeurs autorisées : " +
                        java.util.Arrays.toString(Company.SubscriptionPlan.values()));
            }
        }
    }

    public record ExtendTrialRequest(@NotNull @Positive Integer days) {
        public ExtendTrialRequest {
            if (days == null || days <= 0) throw new IllegalArgumentException("Le nombre de jours doit être positif");
            if (days > 365) throw new IllegalArgumentException("Maximum 365 jours");
        }
    }
}
