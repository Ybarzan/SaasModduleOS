package com.logistix.service;

import com.logistix.dto.PraxioIntegrationSettingsDto;
import com.logistix.model.Company;
import com.logistix.repository.CompanyRepository;
import com.logistix.security.ApiKeyHasher;
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
        // praxioIntegrationApiKey ne contient qu'un hash (voir ApiKeyHasher) : la
        // clé en clair n'est jamais récupérable une fois générée. Si une clé existe
        // déjà, on ne peut donc plus la renvoyer -- comme settings(), qui ne la
        // renvoie jamais non plus. Seule une activation initiale (première
        // génération) retourne la clé en clair, une seule fois.
        String plainKeyToReturn = null;
        if (company.getPraxioIntegrationApiKey() == null) {
            String generated = Company.generatePraxioIntegrationApiKey();
            company.setPraxioIntegrationApiKey(ApiKeyHasher.hash(generated));
            plainKeyToReturn = generated;
        }
        company.setPraxioIntegrationEnabled(true);
        companyRepository.save(company);
        return new PraxioIntegrationSettingsDto(true, plainKeyToReturn);
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
        company.setPraxioIntegrationEnabled(false);
        company.setPraxioIntegrationApiKey(null);
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
        return companyRepository.findByPraxioIntegrationApiKeyAndPraxioIntegrationEnabledTrue(ApiKeyHasher.hash(apiKey))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Clé invalide ou intégration désactivée"))
                .getId();
    }
}
