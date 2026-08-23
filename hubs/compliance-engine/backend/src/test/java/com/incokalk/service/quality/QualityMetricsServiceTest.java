package com.incokalk.service.quality;

import com.incokalk.model.ClientInvoice;
import com.incokalk.model.CustomsDeclaration;
import com.incokalk.model.Discrepancy;
import com.incokalk.model.ReceivingOrderLine;
import com.incokalk.model.ShipmentOrder;
import com.incokalk.repository.ClientInvoiceRepository;
import com.incokalk.repository.CustomsDeclarationRepository;
import com.incokalk.repository.DiscrepancyRepository;
import com.incokalk.repository.ReceivingOrderLineRepository;
import com.incokalk.repository.ShipmentOrderRepository;
import com.incokalk.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("QualityMetricsService — Tests unitaires")
class QualityMetricsServiceTest {

    QualityMetricsService service;
    ShipmentOrderRepository shipmentRepo;
    CustomsDeclarationRepository declarationRepo;
    ReceivingOrderLineRepository lineRepo;
    DiscrepancyRepository discrepancyRepo;
    ClientInvoiceRepository invoiceRepo;
    UUID companyId;

    @BeforeEach
    void setUp() {
        shipmentRepo = mock(ShipmentOrderRepository.class);
        declarationRepo = mock(CustomsDeclarationRepository.class);
        lineRepo = mock(ReceivingOrderLineRepository.class);
        discrepancyRepo = mock(DiscrepancyRepository.class);
        invoiceRepo = mock(ClientInvoiceRepository.class);
        service = new QualityMetricsService(shipmentRepo, declarationRepo, lineRepo, discrepancyRepo, invoiceRepo);
        companyId = UUID.randomUUID();
        TenantContext.set(companyId);

        when(shipmentRepo.findByCompanyIdAndStatus(any(), any())).thenReturn(List.of());
        for (CustomsDeclaration.DeclarationStatus s : CustomsDeclaration.DeclarationStatus.values()) {
            when(declarationRepo.countByCompanyIdAndStatus(any(), eq(s))).thenReturn(0L);
        }
        when(lineRepo.findByCompanyId(any())).thenReturn(List.of());
        when(discrepancyRepo.findByCompanyIdOrderByCreatedAtDesc(any())).thenReturn(List.of());
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

        assertThat(report.characteristics()).hasSize(4);
        assertThat(report.overall().opportunities()).isZero();
        assertThat(report.overall().sigma()).isNull();
    }

    @Test
    @DisplayName("onTimeDelivery → exclut les expéditions sans les deux dates, compte les retards")
    void onTimeDelivery_filtersUnmeasurable_countsLateAsDefect() {
        ShipmentOrder onTime = new ShipmentOrder();
        onTime.setEstimatedDeliveryDate(LocalDateTime.of(2026, 8, 10, 0, 0));
        onTime.setActualDeliveryDate(LocalDateTime.of(2026, 8, 9, 0, 0));

        ShipmentOrder late = new ShipmentOrder();
        late.setEstimatedDeliveryDate(LocalDateTime.of(2026, 8, 10, 0, 0));
        late.setActualDeliveryDate(LocalDateTime.of(2026, 8, 12, 0, 0));

        ShipmentOrder unmeasurable = new ShipmentOrder();
        unmeasurable.setEstimatedDeliveryDate(null);
        unmeasurable.setActualDeliveryDate(LocalDateTime.of(2026, 8, 9, 0, 0));

        when(shipmentRepo.findByCompanyIdAndStatus(companyId, ShipmentOrder.Status.DELIVERED))
                .thenReturn(List.of(onTime, late, unmeasurable));

        QualityMetricsService.QualityReport report = service.getReport();
        QualityMetricsService.Ctq onTimeCtq = report.characteristics().get(0);

        assertThat(onTimeCtq.key()).isEqualTo("on_time_delivery");
        assertThat(onTimeCtq.result().opportunities()).isEqualTo(2);
        assertThat(onTimeCtq.result().defects()).isEqualTo(1);
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
        QualityMetricsService.Ctq customs = report.characteristics().get(1);

        assertThat(customs.key()).isEqualTo("customs_first_pass");
        assertThat(customs.result().opportunities()).isEqualTo(7);
        assertThat(customs.result().defects()).isEqualTo(1);
    }

    @Test
    @DisplayName("receivingAccuracy → exclut quantité reçue nulle, compte les lignes avec écart signalé")
    void receivingAccuracy_excludesZeroQuantity_countsDiscrepantLines() {
        UUID line1 = UUID.randomUUID();
        UUID line3 = UUID.randomUUID();
        ReceivingOrderLine l1 = new ReceivingOrderLine();
        l1.setId(line1);
        l1.setQuantityReceived(new BigDecimal("5"));
        ReceivingOrderLine l2 = new ReceivingOrderLine();
        l2.setId(UUID.randomUUID());
        l2.setQuantityReceived(BigDecimal.ZERO);
        ReceivingOrderLine l3 = new ReceivingOrderLine();
        l3.setId(line3);
        l3.setQuantityReceived(new BigDecimal("2"));

        when(lineRepo.findByCompanyId(companyId)).thenReturn(List.of(l1, l2, l3));

        Discrepancy d1 = new Discrepancy();
        d1.setLineId(line1);
        Discrepancy d2 = new Discrepancy();
        d2.setLineId(null);
        when(discrepancyRepo.findByCompanyIdOrderByCreatedAtDesc(companyId)).thenReturn(List.of(d1, d2));

        QualityMetricsService.QualityReport report = service.getReport();
        QualityMetricsService.Ctq receiving = report.characteristics().get(2);

        assertThat(receiving.key()).isEqualTo("receiving_accuracy");
        assertThat(receiving.result().opportunities()).isEqualTo(2);
        assertThat(receiving.result().defects()).isEqualTo(1);
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
        QualityMetricsService.Ctq invoicing = report.characteristics().get(3);

        assertThat(invoicing.key()).isEqualTo("invoice_timeliness");
        assertThat(invoicing.result().opportunities()).isEqualTo(3);
        assertThat(invoicing.result().defects()).isEqualTo(1);
    }
}
