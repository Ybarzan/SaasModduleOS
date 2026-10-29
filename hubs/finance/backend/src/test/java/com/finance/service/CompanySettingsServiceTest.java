package com.finance.service;

import com.finance.model.Company;
import com.finance.repository.CompanyRepository;
import com.finance.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("CompanySettingsService — Tests unitaires")
class CompanySettingsServiceTest {

    CompanyRepository companyRepo;
    CompanySettingsService service;
    UUID companyId;

    @BeforeEach
    void setUp() {
        companyRepo = mock(CompanyRepository.class);
        service = new CompanySettingsService(companyRepo);
        companyId = UUID.randomUUID();
        TenantContext.set(companyId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("hasFleetHubApiKey → false si aucune clé enregistrée")
    void hasFleetHubApiKey_falseWhenAbsent() {
        when(companyRepo.findById(companyId)).thenReturn(Optional.of(Company.builder().id(companyId).build()));

        assertThat(service.hasFleetHubApiKey()).isFalse();
    }

    @Test
    @DisplayName("setFleetHubApiKey puis hasFleetHubApiKey → true")
    void setFleetHubApiKey_thenHasKey_true() {
        Company company = Company.builder().id(companyId).build();
        when(companyRepo.findById(companyId)).thenReturn(Optional.of(company));

        service.setFleetHubApiKey("  abc123  ");

        assertThat(company.getFleetHubApiKey()).isEqualTo("abc123");
        verify(companyRepo).save(company);
        assertThat(service.hasFleetHubApiKey()).isTrue();
    }

    @Test
    @DisplayName("setFleetHubApiKey avec valeur vide → efface la clé")
    void setFleetHubApiKey_blank_clearsKey() {
        Company company = Company.builder().id(companyId).fleetHubApiKey("old-key").build();
        when(companyRepo.findById(companyId)).thenReturn(Optional.of(company));

        service.setFleetHubApiKey("   ");

        assertThat(company.getFleetHubApiKey()).isNull();
    }
}
