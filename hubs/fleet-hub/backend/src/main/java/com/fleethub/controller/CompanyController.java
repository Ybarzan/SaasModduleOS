package com.fleethub.controller;

import com.fleethub.config.ResourceNotFoundException;
import com.fleethub.dto.CompanyProfileDto;
import com.fleethub.dto.FleetProfileRequest;
import com.fleethub.model.Company;
import com.fleethub.repository.CompanyRepository;
import com.fleethub.security.TenantContext;
import com.fleethub.service.AuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Profil de la société courante : métier de la flotte (poids lourds,
 * messagerie, collecte santé, mixte) qui pilote les modules mis en avant.
 */
@RestController
@RequestMapping("/api/company")
@RequiredArgsConstructor
@Tag(name = "Société", description = "Profil métier de la société courante")
public class CompanyController {

    private final CompanyRepository companyRepository;
    private final AuditService auditService;

    @GetMapping
    @Operation(summary = "Profil de la société", description = "Nom, plan et profil de flotte de la société courante")
    public CompanyProfileDto get() {
        return CompanyProfileDto.of(load());
    }

    @PutMapping("/fleet-profile")
    @Transactional
    @Operation(summary = "Changer le profil de flotte (ADMIN)",
            description = "POIDS_LOURD, MESSAGERIE, COLLECTE_SANTE ou MIXTE")
    public CompanyProfileDto updateFleetProfile(@Valid @RequestBody FleetProfileRequest req) {
        Company company = load();
        Company.FleetProfile profile = Company.FleetProfile.valueOf(req.fleetProfile());
        company.setFleetProfile(profile);
        companyRepository.save(company);
        auditService.log("PROFIL_FLOTTE", "Profil de flotte : " + profile.name());
        return CompanyProfileDto.of(company);
    }

    private Company load() {
        return companyRepository.findById(TenantContext.require().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Société introuvable"));
    }
}
