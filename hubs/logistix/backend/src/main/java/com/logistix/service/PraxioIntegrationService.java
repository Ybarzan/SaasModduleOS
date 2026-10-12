package com.logistix.service;

import com.logistix.dto.PraxioIntegrationSettingsDto;
import com.logistix.model.Company;
import com.logistix.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * Lecture opt-in d'un résumé d'activité Logistix par le tableau de bord
 * cross-hub de Praxio. Jamais d'accès direct à la base : Praxio appelle
 * GET /v1/internal/dashboard-summary avec la clé machine-à-machine de la
 * société qui a activé le partage -- même principe que l'intégration
 * entrante de fleet-hub (InternalIntegrationService).
 */
@Service
@RequiredArgsConstructor
public class PraxioIntegrationService {

    private final CompanyRepository companyRepository;

    @Transactional(readOnly = true)
    public PraxioIntegrationSettingsDto settings(UUID companyId) {
        Company company = companyRepository.findById(companyId).orElseThrow();
        return new PraxioIntegrationSettingsDto(company.isPraxioIntegrationEnabled(), null);
    }

    @Transactional
    public PraxioIntegrationSettingsDto enable(UUID companyId) {
        Company company = companyRepository.findById(companyId).orElseThrow();
        if (company.getPraxioIntegrationApiKey() == null) {
            company.setPraxioIntegrationApiKey(Company.generatePraxioIntegrationApiKey());
        }
        company.setPraxioIntegrationEnabled(true);
        companyRepository.save(company);
        return new PraxioIntegrationSettingsDto(true, company.getPraxioIntegrationApiKey());
    }

    @Transactional
    public void disable(UUID companyId) {
        Company company = companyRepository.findById(companyId).orElseThrow();
        company.setPraxioIntegrationEnabled(false);
        companyRepository.save(company);
    }

    /**
     * Résout la société propriétaire de la clé présentée -- jamais depuis un
     * identifiant fourni par l'appelant. Rejette une clé absente, invalide,
     * ou dont le partage a été désactivé depuis.
     */
    @Transactional(readOnly = true)
    public UUID resolveCompanyId(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Clé X-Internal-Integration-Key manquante");
        }
        return companyRepository.findByPraxioIntegrationApiKeyAndPraxioIntegrationEnabledTrue(apiKey)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Clé invalide ou intégration désactivée"))
                .getId();
    }
}
