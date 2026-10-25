package com.incokalk.service.quality;

import com.incokalk.model.CustomsDeclaration;
import com.incokalk.repository.CustomsDeclarationRepository;
import com.incokalk.service.integration.finance.FinanceIntegrationClient;
import com.incokalk.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("QualityMetricsService — Tests unitaires")
class QualityMetricsServiceTest {

    QualityMetricsService service;
    CustomsDeclarationRepository declarationRepo;
    FinanceIntegrationClient financeIntegrationClient;
    UUID companyId;

    @BeforeEach
    void setUp() {
        declarationRepo = mock(CustomsDeclarationRepository.class);
        financeIntegrationClient = mock(FinanceIntegrationClient.class);
        service = new QualityMetricsService(declarationRepo, financeIntegrationClient);
        companyId = UUID.randomUUID();
        TenantContext.set(companyId);

        for (CustomsDeclaration.DeclarationStatus s : CustomsDeclaration.DeclarationStatus.values()) {
            when(declarationRepo.countByCompanyIdAndStatus(any(), eq(s))).thenReturn(0L);
        }
        when(financeIntegrationClient.getInvoiceTimeliness(any()))
                .thenReturn(new FinanceIntegrationClient.InvoiceTimeliness(0, 0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("getReport → toutes les CTQ vides → rapport global vide")
    void getReport_allEmpty_returnsEmptyOverall() {
        QualityMetricsService.QualityReport report = service.getReport();

        assertThat(report.characteristics()).hasSize(2);
        assertThat(report.overall().opportunities()).isZero();
        assertThat(report.overall().sigma()).isNull();
    }

    @Test
    @DisplayName("customsFirstPassYield → somme les 5 statuts soumis, REJECTED compte comme défaut")
    void customsFirstPassYield_sumsSubmittedStatuses_rejectedIsDefect() {
        when(declarationRepo.countByCompanyIdAndStatus(companyId, CustomsDeclaration.DeclarationStatus.SUBMITTED)).thenReturn(2L);
        when(declarationRepo.countByCompanyIdAndStatus(companyId, CustomsDeclaration.DeclarationStatus.UNDER_REVIEW)).thenReturn(1L);
        when(declarationRepo.countByCompanyIdAndStatus(companyId, CustomsDeclaration.DeclarationStatus.CLEARED)).thenReturn(3L);
        when(declarationRepo.countByCompanyIdAndStatus(companyId, CustomsDeclaration.DeclarationStatus.RELEASED)).thenReturn(0L);
        when(declarationRepo.countByCompanyIdAndStatus(companyId, CustomsDeclaration.DeclarationStatus.REJECTED)).thenReturn(1L);

        QualityMetricsService.QualityReport report = service.getReport();
        QualityMetricsService.Ctq customs = report.characteristics().get(0);

        assertThat(customs.key()).isEqualTo("customs_first_pass");
        assertThat(customs.result().opportunities()).isEqualTo(7);
        assertThat(customs.result().defects()).isEqualTo(1);
    }

    @Test
    @DisplayName("invoiceTimeliness → agrège opportunities/defects renvoyés par Finance")
    void invoiceTimeliness_usesFinanceIntegrationClient() {
        when(financeIntegrationClient.getInvoiceTimeliness(companyId))
                .thenReturn(new FinanceIntegrationClient.InvoiceTimeliness(3, 1));

        QualityMetricsService.QualityReport report = service.getReport();
        QualityMetricsService.Ctq invoicing = report.characteristics().get(1);

        assertThat(invoicing.key()).isEqualTo("invoice_timeliness");
        assertThat(invoicing.result().opportunities()).isEqualTo(3);
        assertThat(invoicing.result().defects()).isEqualTo(1);
    }

    @Test
    @DisplayName("invoiceTimeliness → Finance non configurée → dégradation gracieuse (zéro, pas d'exception)")
    void invoiceTimeliness_financeUnavailable_gracefulDegradation() {
        when(financeIntegrationClient.getInvoiceTimeliness(companyId))
                .thenThrow(new FinanceIntegrationClient.FinanceIntegrationException("non configurée"));

        QualityMetricsService.QualityReport report = service.getReport();
        QualityMetricsService.Ctq invoicing = report.characteristics().get(1);

        assertThat(invoicing.result().opportunities()).isZero();
        assertThat(invoicing.result().defects()).isZero();
    }
}
