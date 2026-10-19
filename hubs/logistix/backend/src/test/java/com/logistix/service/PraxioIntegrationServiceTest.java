package com.logistix.service;

import com.logistix.dto.PraxioIntegrationSettingsDto;
import com.logistix.model.Company;
import com.logistix.repository.CompanyRepository;
import com.logistix.security.ApiKeyHasher;
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
    @DisplayName("enable → génère une clé si absente, l'expose une fois en clair, stocke son hash")
    void enable_generatesKey_whenAbsent() {
        Company company = Company.builder().id(companyId).build();
        when(companyRepo.findById(companyId)).thenReturn(Optional.of(company));

        PraxioIntegrationSettingsDto dto = service.enable(companyId);

        assertThat(dto.praxioIntegrationEnabled()).isTrue();
        assertThat(dto.praxioIntegrationApiKey()).isNotBlank();
        // Le champ stocké n'est jamais la clé en clair, seulement son hash (voir
        // ApiKeyHasher) -- la clé en clair n'est récupérable qu'une fois, ici.
        assertThat(company.getPraxioIntegrationApiKey()).isEqualTo(ApiKeyHasher.hash(dto.praxioIntegrationApiKey()));
        verify(companyRepo).save(company);
    }

    @Test
    @DisplayName("enable → réutilise le hash existant sans le régénérer, et ne renvoie plus la clé")
    void enable_reusesExistingKey() {
        String existingHash = ApiKeyHasher.hash("existing-key");
        Company company = Company.builder().id(companyId).praxioIntegrationApiKey(existingHash).build();
        when(companyRepo.findById(companyId)).thenReturn(Optional.of(company));

        PraxioIntegrationSettingsDto dto = service.enable(companyId);

        // Le hash existant ne peut pas être "redéchiffré" en la clé d'origine : la
        // clé en clair n'est donc plus renvoyée sur un appel enable() ultérieur
        // (même règle que settings(), qui ne l'a jamais renvoyée non plus).
        assertThat(dto.praxioIntegrationApiKey()).isNull();
        assertThat(company.getPraxioIntegrationApiKey()).isEqualTo(existingHash);
    }

    @Test
    @DisplayName("disable → désactive et efface le hash (une réactivation en génère un nouveau)")
    void disable_clearsStoredKeyHash() {
        Company company = Company.builder().id(companyId).praxioIntegrationEnabled(true).praxioIntegrationApiKey("k").build();
        when(companyRepo.findById(companyId)).thenReturn(Optional.of(company));

        service.disable(companyId);

        assertThat(company.isPraxioIntegrationEnabled()).isFalse();
        assertThat(company.getPraxioIntegrationApiKey()).isNull();
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
        when(companyRepo.findByPraxioIntegrationApiKeyAndPraxioIntegrationEnabledTrue(ApiKeyHasher.hash("bad-key")))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolveCompanyId("bad-key"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode().value())
                .isEqualTo(401);
    }

    @Test
    @DisplayName("resolveCompanyId → clé valide -> id de la société propriétaire")
    void resolveCompanyId_validKey_returnsCompanyId() {
        Company company = Company.builder().id(companyId).praxioIntegrationEnabled(true)
                .praxioIntegrationApiKey(ApiKeyHasher.hash("good-key")).build();
        when(companyRepo.findByPraxioIntegrationApiKeyAndPraxioIntegrationEnabledTrue(ApiKeyHasher.hash("good-key")))
                .thenReturn(Optional.of(company));

        assertThat(service.resolveCompanyId("good-key")).isEqualTo(companyId);
    }
}
