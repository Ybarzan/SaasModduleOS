package com.incokalk.security;

import com.incokalk.model.Company;
import com.incokalk.repository.CompanyRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Vérifie que fleetHubApiKey/financeApiKey (Company) sont réellement chiffrées
 * en base (EncryptedApiKeyConverter) et correctement déchiffrées à la relecture
 * -- ces clés sortantes ne sont jamais recherchées par égalité (voir
 * EncryptedApiKeyConverter pour la distinction avec les clés entrantes hashées).
 */
@SpringBootTest
@ActiveProfiles("test")
class CompanyApiKeyEncryptionTest {

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("fleetHubApiKey et financeApiKey sont chiffrées en base et déchiffrées à la relecture")
    void apiKeys_areEncryptedAtRest_andDecryptedOnReload() {
        Company company = Company.builder()
                .name("Acme Test")
                .slug("acme-test-" + System.nanoTime())
                .fleetHubApiKey("fh-plaintext-key-123")
                .financeApiKey("fin-plaintext-key-456")
                .build();
        Company saved = companyRepository.save(company);

        String rawFleetHub = jdbcTemplate.queryForObject(
                "SELECT fleethub_api_key FROM companies WHERE id = ?", String.class, saved.getId());
        String rawFinance = jdbcTemplate.queryForObject(
                "SELECT finance_api_key FROM companies WHERE id = ?", String.class, saved.getId());

        assertThat(rawFleetHub).isNotEqualTo("fh-plaintext-key-123");
        assertThat(rawFleetHub).doesNotContain("fh-plaintext-key-123");
        assertThat(rawFinance).isNotEqualTo("fin-plaintext-key-456");
        assertThat(rawFinance).doesNotContain("fin-plaintext-key-456");

        companyRepository.findById(saved.getId()).ifPresentOrElse(reloaded -> {
            assertThat(reloaded.getFleetHubApiKey()).isEqualTo("fh-plaintext-key-123");
            assertThat(reloaded.getFinanceApiKey()).isEqualTo("fin-plaintext-key-456");
        }, () -> {
            throw new AssertionError("Company non retrouvée après sauvegarde");
        });
    }
}
