package com.incokalk.service.integration.finance;

import com.incokalk.model.Company;
import com.incokalk.repository.CompanyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("FinanceIntegrationClient — Authentification par clé d'intégration")
class FinanceIntegrationClientTest {

    private RestTemplate restTemplate;
    private CompanyRepository companyRepository;
    private FinanceIntegrationClient client;
    private UUID companyId;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        companyRepository = mock(CompanyRepository.class);
        client = new FinanceIntegrationClient(restTemplate, companyRepository);
        ReflectionTestUtils.setField(client, "baseUrl", "http://finance.test");
        companyId = UUID.randomUUID();
    }

    @Test
    @DisplayName("getDashboardSummary → entreprise sans clé configurée → erreur explicite, aucun appel HTTP")
    void getDashboardSummary_noKeyConfigured_throwsWithoutCallingFinance() {
        when(companyRepository.findById(companyId)).thenReturn(Optional.of(Company.builder().id(companyId).build()));

        assertThatThrownBy(() -> client.getDashboardSummary(companyId))
                .isInstanceOf(FinanceIntegrationClient.FinanceIntegrationException.class)
                .hasMessageContaining("non configurée");

        verifyNoInteractions(restTemplate);
    }

    @Test
    @DisplayName("getDashboardSummary → entreprise introuvable → erreur explicite, aucun appel HTTP")
    void getDashboardSummary_companyNotFound_throwsWithoutCallingFinance() {
        when(companyRepository.findById(companyId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> client.getDashboardSummary(companyId))
                .isInstanceOf(FinanceIntegrationClient.FinanceIntegrationException.class)
                .hasMessageContaining("non configurée");

        verifyNoInteractions(restTemplate);
    }

    @Test
    @DisplayName("getDashboardSummary → clé configurée → envoie X-Internal-Integration-Key vers /v1/internal/dashboard-summary")
    void getDashboardSummary_withKey_sendsKeyHeaderToInternalEndpoint() {
        when(companyRepository.findById(companyId))
                .thenReturn(Optional.of(Company.builder().id(companyId).financeApiKey("the-key-123").build()));

        FinanceIntegrationClient.DashboardSummary summary =
                new FinanceIntegrationClient.DashboardSummary(3, new BigDecimal("1500.00"));
        when(restTemplate.exchange(eq("http://finance.test/v1/internal/dashboard-summary"),
                eq(HttpMethod.GET), any(HttpEntity.class), eq(FinanceIntegrationClient.DashboardSummary.class)))
                .thenReturn(new ResponseEntity<>(summary, HttpStatus.OK));

        FinanceIntegrationClient.DashboardSummary result = client.getDashboardSummary(companyId);

        assertThat(result.overdueInvoicesCount()).isEqualTo(3);
        assertThat(result.overdueInvoicesAmount()).isEqualTo(new BigDecimal("1500.00"));
        verify(restTemplate).exchange(
                eq("http://finance.test/v1/internal/dashboard-summary"),
                eq(HttpMethod.GET),
                argThat((HttpEntity<?> entity) -> "the-key-123".equals(entity.getHeaders().getFirst("X-Internal-Integration-Key"))),
                eq(FinanceIntegrationClient.DashboardSummary.class));
    }

    @Test
    @DisplayName("getFinancialTotals → clé configurée → envoie la clé vers /v1/internal/financial-totals")
    void getFinancialTotals_withKey_sendsKeyHeaderToInternalEndpoint() {
        when(companyRepository.findById(companyId))
                .thenReturn(Optional.of(Company.builder().id(companyId).financeApiKey("the-key-123").build()));

        FinanceIntegrationClient.FinancialTotals totals = new FinanceIntegrationClient.FinancialTotals(
                new BigDecimal("10000"), new BigDecimal("6000"), new BigDecimal("4000"));
        when(restTemplate.exchange(eq("http://finance.test/v1/internal/financial-totals"),
                eq(HttpMethod.GET), any(HttpEntity.class), eq(FinanceIntegrationClient.FinancialTotals.class)))
                .thenReturn(new ResponseEntity<>(totals, HttpStatus.OK));

        FinanceIntegrationClient.FinancialTotals result = client.getFinancialTotals(companyId);

        assertThat(result.totalMargin()).isEqualTo(new BigDecimal("4000"));
    }

    @Test
    @DisplayName("getDashboardSummary → Finance renvoie 401 (clé invalide côté Finance) → erreur propagée")
    void getDashboardSummary_financeRejectsKey_propagatesError() {
        when(companyRepository.findById(companyId))
                .thenReturn(Optional.of(Company.builder().id(companyId).financeApiKey("stale-key").build()));
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(FinanceIntegrationClient.DashboardSummary.class)))
                .thenThrow(new org.springframework.web.client.HttpClientErrorException(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> client.getDashboardSummary(companyId))
                .isInstanceOf(FinanceIntegrationClient.FinanceIntegrationException.class);
    }
}
