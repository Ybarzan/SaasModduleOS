package com.incokalk.service.quality;

import com.incokalk.model.ClientInvoice;
import com.incokalk.model.CustomsDeclaration;
import com.incokalk.repository.ClientInvoiceRepository;
import com.incokalk.repository.CustomsDeclarationRepository;
import com.incokalk.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("QualityMetricsService — Tests unitaires")
class QualityMetricsServiceTest {

    QualityMetricsService service;
    CustomsDeclarationRepository declarationRepo;
    ClientInvoiceRepository invoiceRepo;
    UUID companyId;

    @BeforeEach
    void setUp() {
        declarationRepo = mock(CustomsDeclarationRepository.class);
        invoiceRepo = mock(ClientInvoiceRepository.class);
        service = new QualityMetricsService(declarationRepo, invoiceRepo);
        companyId = UUID.randomUUID();
        TenantContext.set(companyId);

        for (CustomsDeclaration.DeclarationStatus s : CustomsDeclaration.DeclarationStatus.values()) {
            when(declarationRepo.countByCompanyIdAndStatus(any(), eq(s))).thenReturn(0L);
        }
        when(invoiceRepo.findByCompanyIdOrderByCreatedAtDesc(any())).thenReturn(List.of());
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
    @DisplayName("invoiceTimeliness → exclut DRAFT, exclut PAID/CANCELLED du calcul de retard, compte les échues")
    void invoiceTimeliness_excludesDraftAndPaid_countsOverdue() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        LocalDate tomorrow = LocalDate.now().plusDays(1);

        ClientInvoice draft = new ClientInvoice();
        draft.setStatus(ClientInvoice.InvoiceStatus.DRAFT);
        draft.setDueDate(yesterday);

        ClientInvoice overdueUnpaid = new ClientInvoice();
        overdueUnpaid.setStatus(ClientInvoice.InvoiceStatus.SENT);
        overdueUnpaid.setDueDate(yesterday);

        ClientInvoice overduePaid = new ClientInvoice();
        overduePaid.setStatus(ClientInvoice.InvoiceStatus.PAID);
        overduePaid.setDueDate(yesterday);

        ClientInvoice notYetDue = new ClientInvoice();
        notYetDue.setStatus(ClientInvoice.InvoiceStatus.SENT);
        notYetDue.setDueDate(tomorrow);

        when(invoiceRepo.findByCompanyIdOrderByCreatedAtDesc(companyId))
                .thenReturn(List.of(draft, overdueUnpaid, overduePaid, notYetDue));

        QualityMetricsService.QualityReport report = service.getReport();
        QualityMetricsService.Ctq invoicing = report.characteristics().get(1);

        assertThat(invoicing.key()).isEqualTo("invoice_timeliness");
        assertThat(invoicing.result().opportunities()).isEqualTo(3);
        assertThat(invoicing.result().defects()).isEqualTo(1);
    }
}
