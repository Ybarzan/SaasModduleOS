package com.fleethub.service;

import com.fleethub.config.ApiKeyCrypto;
import com.fleethub.dto.ComplianceEngineWebhookSettingsDto;
import com.fleethub.model.Company;
import com.fleethub.repository.CompanyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("ComplianceEngineWebhookService — Tests unitaires")
class ComplianceEngineWebhookServiceTest {

    CompanyRepository companyRepository;
    ApiKeyCrypto apiKeyCrypto;
    ComplianceEngineWebhookService service;

    @BeforeEach
    void setUp() {
        companyRepository = mock(CompanyRepository.class);
        // ApiKeyCrypto est un composant pur (pas d'accès réseau/DB) -- instancié
        // directement avec un secret de test, comme pour les autres tests
        // utilisant une dérivation AES-GCM (ex. CredentialEncryptionServiceTest
        // côté compliance-engine).
        apiKeyCrypto = new ApiKeyCrypto("test-secret-not-for-production-use");
        service = new ComplianceEngineWebhookService(companyRepository, apiKeyCrypto);
    }

    private Company company() {
        Company company = new Company();
        company.setId(1L);
        return company;
    }

    @Test
    @DisplayName("settings() → configured=false quand aucune clé n'est stockée")
    void settings_reportsNotConfigured_whenNoKey() {
        when(companyRepository.findById(1L)).thenReturn(Optional.of(company()));

        ComplianceEngineWebhookSettingsDto dto = service.settings(1L);

        assertThat(dto.configured()).isFalse();
    }

    @Test
    @DisplayName("update() → stocke la clé chiffrée (jamais en clair) et configured=true")
    void update_storesEncryptedKey_andReportsConfigured() {
        Company company = company();
        when(companyRepository.findById(1L)).thenReturn(Optional.of(company));

        ComplianceEngineWebhookSettingsDto dto = service.update(1L, "plain-webhook-key");

        assertThat(dto.configured()).isTrue();
        // Jamais la clé en clair stockée sur l'entité -- seulement chiffrée
        // (voir ApiKeyCrypto), déchiffrable pour être rejouée (ShipmentEventPublisher).
        assertThat(company.getComplianceEngineWebhookKey()).isNotEqualTo("plain-webhook-key");
        assertThat(apiKeyCrypto.decrypt(company.getComplianceEngineWebhookKey())).isEqualTo("plain-webhook-key");
        verify(companyRepository).save(company);
    }

    @Test
    @DisplayName("clear() → remet la clé à null et configured=false")
    void clear_nullsOutKey() {
        Company company = company();
        company.setComplianceEngineWebhookKey("some-key");
        when(companyRepository.findById(1L)).thenReturn(Optional.of(company));

        ComplianceEngineWebhookSettingsDto dto = service.clear(1L);

        assertThat(dto.configured()).isFalse();
        assertThat(company.getComplianceEngineWebhookKey()).isNull();
        verify(companyRepository).save(company);
    }

    @Test
    @DisplayName("settings() → configured=true quand une clé non vide est déjà stockée")
    void settings_reportsConfigured_whenKeyPresent() {
        Company company = company();
        company.setComplianceEngineWebhookKey("existing-key");
        when(companyRepository.findById(1L)).thenReturn(Optional.of(company));

        ComplianceEngineWebhookSettingsDto dto = service.settings(1L);

        assertThat(dto.configured()).isTrue();
    }
}
