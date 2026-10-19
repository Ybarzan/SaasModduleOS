package com.finance.service;

import com.finance.dto.InternalIntegrationSettingsDto;
import com.finance.model.Company;
import com.finance.repository.CompanyRepository;
import com.finance.security.ApiKeyHasher;
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
        // internalIntegrationApiKey ne contient qu'un hash (voir ApiKeyHasher) : la
        // clé en clair n'est jamais récupérable une fois générée. Si une clé existe
        // déjà, on ne peut donc plus la renvoyer -- comme settings(), qui ne la
        // renvoie jamais non plus. Seule une activation initiale (première
        // génération) retourne la clé en clair, une seule fois.
        String plainKeyToReturn = null;
        if (company.getInternalIntegrationApiKey() == null) {
            String generated = Company.generateApiKey();
            company.setInternalIntegrationApiKey(ApiKeyHasher.hash(generated));
            plainKeyToReturn = generated;
        }
        company.setInternalIntegrationEnabled(true);
        companyRepository.save(company);
        return new InternalIntegrationSettingsDto(true, plainKeyToReturn);
    }

    /**
     * La clé stockée n'étant qu'un hash (voir enable()), elle ne peut plus être
     * réaffichée après coup -- on l'efface donc à la désactivation : une
     * réactivation ultérieure en génère une nouvelle plutôt que de laisser une
     * clé orpheline, invisible mais toujours présente en base, sans utilité.
     */
    @Transactional
    public void disable(UUID companyId) {
        Company company = companyRepository.findById(companyId).orElseThrow();
        company.setInternalIntegrationEnabled(false);
        company.setInternalIntegrationApiKey(null);
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
        return companyRepository.findByInternalIntegrationApiKeyAndInternalIntegrationEnabledTrue(ApiKeyHasher.hash(apiKey))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Clé invalide ou intégration désactivée"))
                .getId();
    }
}
