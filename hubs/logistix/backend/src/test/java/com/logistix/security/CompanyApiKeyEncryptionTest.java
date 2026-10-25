package com.logistix.security;

import com.logistix.model.Company;
import com.logistix.repository.CompanyRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Vérifie que fleetHubApiKey (Company) est réellement chiffrée en base
 * (EncryptedApiKeyConverter) et correctement déchiffrée à la relecture --
 * cette clé sortante n'est jamais recherchée par égalité (voir
 * EncryptedApiKeyConverter pour la distinction avec praxioIntegrationApiKey,
 * une clé entrante hashée plutôt que chiffrée).
 */
@SpringBootTest
class CompanyApiKeyEncryptionTest {

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("fleetHubApiKey est chiffrée en base et déchiffrée à la relecture")
    void fleetHubApiKey_isEncryptedAtRest_andDecryptedOnReload() {
        Company company = Company.builder()
                .name("Acme Test")
                .fleetHubApiKey("fh-plaintext-key-123")
                .build();
        Company saved = companyRepository.save(company);

        String rawValue = jdbcTemplate.queryForObject(
                "SELECT fleethub_api_key FROM companies WHERE id = ?", String.class, saved.getId());
        assertThat(rawValue).isNotEqualTo("fh-plaintext-key-123");
        assertThat(rawValue).doesNotContain("fh-plaintext-key-123");

        Company reloaded = companyRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getFleetHubApiKey()).isEqualTo("fh-plaintext-key-123");
    }
}
