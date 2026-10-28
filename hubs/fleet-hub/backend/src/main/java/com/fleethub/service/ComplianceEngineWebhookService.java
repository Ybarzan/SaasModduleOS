package com.fleethub.service;

import com.fleethub.config.ApiKeyCrypto;
import com.fleethub.dto.ComplianceEngineWebhookSettingsDto;
import com.fleethub.model.Company;
import com.fleethub.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Collage, côté fleet-hub, de la clé webhook générée par compliance-engine
 * (voir ShipmentEventPublisher) -- même principe inverse que
 * InternalIntegrationService / MarketplaceService (qui génèrent une clé
 * ICI, à coller dans un AUTRE hub) : ici la clé vient de compliance-engine,
 * fleet-hub ne fait que la stocker chiffrée (réversible) pour pouvoir la
 * rejouer dans l'en-tête sortant -- réutilise {@link ApiKeyCrypto}, déjà en
 * place pour les clés API fournisseurs de la page "Intégrations", plutôt
 * qu'un nouveau mécanisme de chiffrement dédié.
 */
@Service
@RequiredArgsConstructor
public class ComplianceEngineWebhookService {

    private final CompanyRepository companyRepository;
    private final ApiKeyCrypto apiKeyCrypto;

    @Transactional(readOnly = true)
    public ComplianceEngineWebhookSettingsDto settings(Long companyId) {
        Company company = companyRepository.findById(companyId).orElseThrow();
        return toDto(company);
    }

    @Transactional
    public ComplianceEngineWebhookSettingsDto update(Long companyId, String plainKey) {
        Company company = companyRepository.findById(companyId).orElseThrow();
        company.setComplianceEngineWebhookKey(apiKeyCrypto.encrypt(plainKey));
        companyRepository.save(company);
        return toDto(company);
    }

    @Transactional
    public ComplianceEngineWebhookSettingsDto clear(Long companyId) {
        Company company = companyRepository.findById(companyId).orElseThrow();
        company.setComplianceEngineWebhookKey(null);
        companyRepository.save(company);
        return toDto(company);
    }

    private ComplianceEngineWebhookSettingsDto toDto(Company company) {
        String key = company.getComplianceEngineWebhookKey();
        return new ComplianceEngineWebhookSettingsDto(key != null && !key.isBlank());
    }
}
