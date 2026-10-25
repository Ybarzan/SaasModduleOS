package com.logistix.service;

import com.logistix.dto.DashboardSummaryDto;
import com.logistix.model.Discrepancy;
import com.logistix.model.ReceivingOrder;
import com.logistix.repository.DiscrepancyRepository;
import com.logistix.repository.ReceivingOrderRepository;
import com.logistix.repository.WarehouseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("DashboardSummaryService — Tests unitaires")
class DashboardSummaryServiceTest {

    WarehouseRepository warehouseRepo;
    ReceivingOrderRepository receivingOrderRepo;
    DiscrepancyRepository discrepancyRepo;
    DashboardSummaryService service;
    UUID companyId;

    @BeforeEach
    void setUp() {
        warehouseRepo = mock(WarehouseRepository.class);
        receivingOrderRepo = mock(ReceivingOrderRepository.class);
        discrepancyRepo = mock(DiscrepancyRepository.class);
        service = new DashboardSummaryService(warehouseRepo, receivingOrderRepo, discrepancyRepo);
        companyId = UUID.randomUUID();
    }

    @Test
    @DisplayName("getDashboardSummary → agrège entrepôts actifs/réceptions ouvertes/écarts ouverts")
    void getDashboardSummary_aggregatesKpis() {
        when(warehouseRepo.countByCompanyIdAndIsActiveTrue(companyId)).thenReturn(3L);
        when(receivingOrderRepo.countByCompanyIdAndStatusIn(eq(companyId), any())).thenReturn(5L);
        when(discrepancyRepo.countByCompanyIdAndResolutionStatus(companyId, Discrepancy.ResolutionStatus.OPEN)).thenReturn(2L);

        DashboardSummaryDto dto = service.getDashboardSummary(companyId);

        assertThat(dto.activeWarehouses()).isEqualTo(3L);
        assertThat(dto.openReceivingOrders()).isEqualTo(5L);
        assertThat(dto.openDiscrepancies()).isEqualTo(2L);
    }

    @Test
    @DisplayName("getDashboardSummary → ne compte que les commandes de réception ouvertes (DRAFT/RECEIVING)")
    void getDashboardSummary_usesOpenReceivingStatuses() {
        when(receivingOrderRepo.countByCompanyIdAndStatusIn(eq(companyId), any())).thenReturn(0L);

        service.getDashboardSummary(companyId);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ReceivingOrder.Status>> captor = ArgumentCaptor.forClass(List.class);
        verify(receivingOrderRepo).countByCompanyIdAndStatusIn(eq(companyId), captor.capture());
        assertThat(captor.getValue()).containsExactlyInAnyOrder(ReceivingOrder.Status.DRAFT, ReceivingOrder.Status.RECEIVING);
    }
}
