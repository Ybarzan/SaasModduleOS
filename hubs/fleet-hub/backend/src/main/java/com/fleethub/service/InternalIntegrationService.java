package com.fleethub.service;

import com.fleethub.dto.InternalIntegrationSettingsDto;
import com.fleethub.model.Company;
import com.fleethub.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Lecture opt-in d'expéditions par compliance-engine ou Logistix (dérivation
 * de documents douaniers / lignes de réception). Jamais d'accès direct entre
 * les bases : ces hubs appellent GET /api/internal/shipments/** avec la clé
 * machine-à-machine de la société qui a activé le partage -- même principe
 * que {@link MarketplaceService} vers FleetMarket.
 */
@Service
@RequiredArgsConstructor
public class InternalIntegrationService {

    private final CompanyRepository companyRepository;

    @Transactional(readOnly = true)
    public InternalIntegrationSettingsDto settings(Long companyId) {
        Company company = companyRepository.findById(companyId).orElseThrow();
        return new InternalIntegrationSettingsDto(company.isInternalIntegrationEnabled(), null);
    }

    @Transactional
    public InternalIntegrationSettingsDto enable(Long companyId) {
        Company company = companyRepository.findById(companyId).orElseThrow();
        if (company.getInternalIntegrationApiKey() == null) {
            company.setInternalIntegrationApiKey(Company.generateInternalIntegrationApiKey());
        }
        company.setInternalIntegrationEnabled(true);
        companyRepository.save(company);
        return new InternalIntegrationSettingsDto(true, company.getInternalIntegrationApiKey());
    }

    @Transactional
    public void disable(Long companyId) {
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
    public Long resolveCompanyId(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Clé X-Internal-Integration-Key manquante");
        }
        return companyRepository.findByInternalIntegrationApiKeyAndInternalIntegrationEnabledTrue(apiKey)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Clé invalide ou intégration désactivée"))
                .getId();
    }
}
