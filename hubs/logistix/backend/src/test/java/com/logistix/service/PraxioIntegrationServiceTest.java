package com.logistix.service;

import com.logistix.dto.PraxioIntegrationSettingsDto;
import com.logistix.model.Company;
import com.logistix.repository.CompanyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("PraxioIntegrationService — Tests unitaires")
class PraxioIntegrationServiceTest {

    CompanyRepository companyRepo;
    PraxioIntegrationService service;
    UUID companyId;

    @BeforeEach
    void setUp() {
        companyRepo = mock(CompanyRepository.class);
        service = new PraxioIntegrationService(companyRepo);
        companyId = UUID.randomUUID();
    }

    @Test
    @DisplayName("settings → désactivé par défaut, jamais de clé renvoyée")
    void settings_defaultsToDisabled() {
        when(companyRepo.findById(companyId)).thenReturn(Optional.of(Company.builder().id(companyId).build()));

        PraxioIntegrationSettingsDto dto = service.settings(companyId);

        assertThat(dto.praxioIntegrationEnabled()).isFalse();
        assertThat(dto.praxioIntegrationApiKey()).isNull();
    }

    @Test
    @DisplayName("enable → génère une clé si absente et l'expose une fois")
    void enable_generatesKey_whenAbsent() {
        Company company = Company.builder().id(companyId).build();
        when(companyRepo.findById(companyId)).thenReturn(Optional.of(company));

        PraxioIntegrationSettingsDto dto = service.enable(companyId);

        assertThat(dto.praxioIntegrationEnabled()).isTrue();
        assertThat(dto.praxioIntegrationApiKey()).isNotBlank();
        assertThat(company.getPraxioIntegrationApiKey()).isEqualTo(dto.praxioIntegrationApiKey());
        verify(companyRepo).save(company);
    }

    @Test
    @DisplayName("enable → réutilise la clé existante plutôt que d'en regénérer une")
    void enable_reusesExistingKey() {
        Company company = Company.builder().id(companyId).praxioIntegrationApiKey("existing-key").build();
        when(companyRepo.findById(companyId)).thenReturn(Optional.of(company));

        PraxioIntegrationSettingsDto dto = service.enable(companyId);

        assertThat(dto.praxioIntegrationApiKey()).isEqualTo("existing-key");
    }

    @Test
    @DisplayName("disable → désactive sans effacer la clé")
    void disable_disablesWithoutClearingKey() {
        Company company = Company.builder().id(companyId).praxioIntegrationEnabled(true).praxioIntegrationApiKey("k").build();
        when(companyRepo.findById(companyId)).thenReturn(Optional.of(company));

        service.disable(companyId);

        assertThat(company.isPraxioIntegrationEnabled()).isFalse();
        assertThat(company.getPraxioIntegrationApiKey()).isEqualTo("k");
    }

    @Test
    @DisplayName("resolveCompanyId → clé absente -> 401")
    void resolveCompanyId_missingKey_unauthorized() {
        assertThatThrownBy(() -> service.resolveCompanyId(null))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode().value())
                .isEqualTo(401);
    }

    @Test
    @DisplayName("resolveCompanyId → clé invalide ou désactivée -> 401")
    void resolveCompanyId_invalidKey_unauthorized() {
        when(companyRepo.findByPraxioIntegrationApiKeyAndPraxioIntegrationEnabledTrue("bad-key"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolveCompanyId("bad-key"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode().value())
                .isEqualTo(401);
    }

    @Test
    @DisplayName("resolveCompanyId → clé valide -> id de la société propriétaire")
    void resolveCompanyId_validKey_returnsCompanyId() {
        Company company = Company.builder().id(companyId).praxioIntegrationEnabled(true).praxioIntegrationApiKey("good-key").build();
        when(companyRepo.findByPraxioIntegrationApiKeyAndPraxioIntegrationEnabledTrue("good-key"))
                .thenReturn(Optional.of(company));

        assertThat(service.resolveCompanyId("good-key")).isEqualTo(companyId);
    }
}
