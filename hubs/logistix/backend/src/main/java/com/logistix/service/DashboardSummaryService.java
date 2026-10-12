package com.logistix.service;

import com.logistix.dto.DashboardSummaryDto;
import com.logistix.model.Discrepancy;
import com.logistix.model.ReceivingOrder;
import com.logistix.repository.DiscrepancyRepository;
import com.logistix.repository.ReceivingOrderRepository;
import com.logistix.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Résumé compact pour le tableau de bord cross-hub de Praxio. Appelé via la
 * clé d'intégration Praxio (PraxioIntegrationService) : le companyId vient de
 * la clé résolue, jamais d'un paramètre client.
 */
@Service
@RequiredArgsConstructor
public class DashboardSummaryService {

    private static final List<ReceivingOrder.Status> OPEN_RECEIVING_STATUSES =
            List.of(ReceivingOrder.Status.DRAFT, ReceivingOrder.Status.RECEIVING);

    private final WarehouseRepository warehouseRepository;
    private final ReceivingOrderRepository receivingOrderRepository;
    private final DiscrepancyRepository discrepancyRepository;

    @Transactional(readOnly = true)
    public DashboardSummaryDto getDashboardSummary(UUID companyId) {
        long activeWarehouses = warehouseRepository.countByCompanyIdAndIsActiveTrue(companyId);
        long openReceivingOrders = receivingOrderRepository.countByCompanyIdAndStatusIn(companyId, OPEN_RECEIVING_STATUSES);
        long openDiscrepancies = discrepancyRepository.countByCompanyIdAndResolutionStatus(
                companyId, Discrepancy.ResolutionStatus.OPEN);

        return new DashboardSummaryDto(activeWarehouses, openReceivingOrders, openDiscrepancies);
    }
}
