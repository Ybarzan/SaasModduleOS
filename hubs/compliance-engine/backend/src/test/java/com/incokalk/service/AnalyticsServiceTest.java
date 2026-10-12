package com.incokalk.service;

import com.incokalk.dto.analytics.DashboardStatsDTO;
import com.incokalk.dto.analytics.DashboardSummaryDTO;
import com.incokalk.model.ClientInvoice;
import com.incokalk.model.CustomsDeclaration;
import com.incokalk.repository.ClientInvoiceRepository;
import com.incokalk.repository.CustomsDeclarationRepository;
import com.incokalk.repository.SimulationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Toutes les méthodes sauf getDashboardStats ont été retirées avec le domaine
 * Transport (voir AnalyticsService) ; getDashboardStats elle-même est réduite à
 * ses deux champs indépendants du domaine Transport (totalSimulations/
 * simulationsThisMonth, via SimulationRepository).
 */
@DisplayName("AnalyticsService — Tests unitaires")
class AnalyticsServiceTest {

    @Mock SimulationRepository simulationRepo;
    @Mock CustomsDeclarationRepository customsDeclarationRepo;
    @Mock ClientInvoiceRepository clientInvoiceRepo;
    @InjectMocks AnalyticsService service;

    UUID companyId;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        companyId = UUID.randomUUID();
    }

    @Test
    @DisplayName("getDashboardStats → renvoie totalSimulations/simulationsThisMonth")
    void getDashboardStats_returnsSimulationCounts() {
        when(simulationRepo.countByCompanyId(companyId)).thenReturn(10L);
        when(simulationRepo.countByCompanyIdAndCreatedAtAfter(eq(companyId), any(LocalDateTime.class))).thenReturn(3L);

        DashboardStatsDTO stats = service.getDashboardStats(companyId, "30d");

        assertThat(stats.getTotalSimulations()).isEqualTo(10L);
        assertThat(stats.getSimulationsThisMonth()).isEqualTo(3L);
        assertThat(stats.getPeriod()).isEqualTo("30d");
    }

    @Test
    @DisplayName("getDashboardSummary → agrège simulations/déclarations ouvertes/factures en retard")
    void getDashboardSummary_aggregatesKpis() {
        when(simulationRepo.countByCompanyIdAndCreatedAtAfter(eq(companyId), any(LocalDateTime.class))).thenReturn(5L);
        when(customsDeclarationRepo.countByCompanyIdAndStatusIn(eq(companyId), any(List.class))).thenReturn(7L);
        when(clientInvoiceRepo.countByCompanyIdAndDueDateBeforeAndStatusNotIn(eq(companyId), any(LocalDate.class), any(List.class)))
                .thenReturn(2L);
        when(clientInvoiceRepo.sumTotalAmountByCompanyIdAndDueDateBeforeAndStatusNotIn(eq(companyId), any(LocalDate.class), any(List.class)))
                .thenReturn(new BigDecimal("1234.50"));

        DashboardSummaryDTO summary = service.getDashboardSummary(companyId);

        assertThat(summary.getSimulationsThisMonth()).isEqualTo(5L);
        assertThat(summary.getOpenDeclarations()).isEqualTo(7L);
        assertThat(summary.getOverdueInvoicesCount()).isEqualTo(2L);
        assertThat(summary.getOverdueInvoicesAmount()).isEqualByComparingTo("1234.50");
    }

    @Test
    @DisplayName("getDashboardSummary → n'interroge que les statuts de déclaration ouverts")
    void getDashboardSummary_usesOpenDeclarationStatuses() {
        when(customsDeclarationRepo.countByCompanyIdAndStatusIn(eq(companyId), any(List.class))).thenReturn(0L);
        when(clientInvoiceRepo.sumTotalAmountByCompanyIdAndDueDateBeforeAndStatusNotIn(eq(companyId), any(LocalDate.class), any(List.class)))
                .thenReturn(BigDecimal.ZERO);

        service.getDashboardSummary(companyId);

        org.mockito.ArgumentCaptor<List<CustomsDeclaration.DeclarationStatus>> captor =
                org.mockito.ArgumentCaptor.forClass(List.class);
        org.mockito.Mockito.verify(customsDeclarationRepo).countByCompanyIdAndStatusIn(eq(companyId), captor.capture());
        assertThat(captor.getValue()).containsExactlyInAnyOrder(
                CustomsDeclaration.DeclarationStatus.DRAFT,
                CustomsDeclaration.DeclarationStatus.SUBMITTED,
                CustomsDeclaration.DeclarationStatus.UNDER_REVIEW);

        org.mockito.ArgumentCaptor<List<ClientInvoice.InvoiceStatus>> invoiceCaptor =
                org.mockito.ArgumentCaptor.forClass(List.class);
        org.mockito.Mockito.verify(clientInvoiceRepo).sumTotalAmountByCompanyIdAndDueDateBeforeAndStatusNotIn(
                eq(companyId), any(LocalDate.class), invoiceCaptor.capture());
        assertThat(invoiceCaptor.getValue()).containsExactlyInAnyOrder(
                ClientInvoice.InvoiceStatus.PAID, ClientInvoice.InvoiceStatus.CANCELLED);
    }
}
