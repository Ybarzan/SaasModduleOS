package com.fleethub.service;

import com.fleethub.config.ApiKeyCrypto;
import com.fleethub.dto.ComplianceEngineWebhookSettingsDto;
import com.fleethub.model.Company;
import com.fleethub.model.OutboundEvent;
import com.fleethub.repository.CompanyRepository;
import com.fleethub.repository.OutboundEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

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
    private final OutboundEventRepository outboundEventRepository;

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

    /** Remet en file les événements abandonnés (ex. après correction de la clé côté compliance-engine). */
    @Transactional
    public ComplianceEngineWebhookSettingsDto retryFailed(Long companyId) {
        Company company = companyRepository.findById(companyId).orElseThrow();
        LocalDateTime now = LocalDateTime.now();
        for (OutboundEvent event : outboundEventRepository.findByCompanyIdAndStatus(companyId, OutboundEvent.Status.FAILED)) {
            event.setStatus(OutboundEvent.Status.PENDING);
            event.setAttempts(0);
            event.setNextAttemptAt(now);
            outboundEventRepository.save(event);
        }
        return toDto(company);
    }

    private ComplianceEngineWebhookSettingsDto toDto(Company company) {
        String key = company.getComplianceEngineWebhookKey();
        Long id = company.getId();
        String lastError = outboundEventRepository
                .findFirstByCompanyIdAndStatusOrderByIdDesc(id, OutboundEvent.Status.FAILED)
                .or(() -> outboundEventRepository.findFirstByCompanyIdAndStatusOrderByIdAsc(id, OutboundEvent.Status.PENDING))
                .map(OutboundEvent::getLastError)
                .orElse(null);
        return new ComplianceEngineWebhookSettingsDto(
                key != null && !key.isBlank(),
                outboundEventRepository.countByCompanyIdAndStatus(id, OutboundEvent.Status.PENDING),
                outboundEventRepository.countByCompanyIdAndStatus(id, OutboundEvent.Status.FAILED),
                lastError);
    }
}
