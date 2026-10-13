package com.finance.service;

import com.finance.dto.InternalIntegrationSettingsDto;
import com.finance.model.Company;
import com.finance.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * Lecture opt-in d'agrégats Finance par des appelants machine-à-machine --
 * Praxio (tableau de bord cross-hub) et compliance-engine
 * (FinanceIntegrationClient) partagent la même clé par société. Jamais
 * d'accès direct à la base : ces appelants passent par GET /v1/internal/**
 * avec la clé de la société qui a activé le partage -- même principe que
 * l'intégration entrante de fleet-hub (InternalIntegrationService).
 */
@Service
@RequiredArgsConstructor
public class InternalIntegrationService {

    private final CompanyRepository companyRepository;

    @Transactional(readOnly = true)
    public InternalIntegrationSettingsDto settings(UUID companyId) {
        Company company = companyRepository.findById(companyId).orElseThrow();
        return new InternalIntegrationSettingsDto(company.isInternalIntegrationEnabled(), null);
    }

    @Transactional
    public InternalIntegrationSettingsDto enable(UUID companyId) {
        Company company = companyRepository.findById(companyId).orElseThrow();
        if (company.getInternalIntegrationApiKey() == null) {
            company.setInternalIntegrationApiKey(Company.generateApiKey());
        }
        company.setInternalIntegrationEnabled(true);
        companyRepository.save(company);
        return new InternalIntegrationSettingsDto(true, company.getInternalIntegrationApiKey());
    }

    @Transactional
    public void disable(UUID companyId) {
        Company company = companyRepository.findById(companyId).orElseThrow();
        company.setInternalIntegrationEnabled(false);
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
        return companyRepository.findByInternalIntegrationApiKeyAndInternalIntegrationEnabledTrue(apiKey)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Clé invalide ou intégration désactivée"))
                .getId();
    }
}
