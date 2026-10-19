package com.finance.service;

import com.finance.dto.internal.CarbonTotalsDto;
import com.finance.dto.internal.DashboardSummaryDto;
import com.finance.dto.internal.FinancialTotalsDto;
import com.finance.dto.internal.InvoiceTimelinessDto;
import com.finance.model.ClientInvoice.InvoiceStatus;
import com.finance.repository.CarbonOffsetRepository;
import com.finance.repository.ClientInvoiceRepository;
import com.finance.repository.ShipmentFinancialsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Agrégats Finance pour les appelants machine-à-machine (Praxio,
 * compliance-engine via FinanceIntegrationClient). Le companyId vient de la
 * clé résolue par {@link InternalIntegrationService}, jamais d'un paramètre
 * client -- même principe que DashboardSummaryService côté Logistix.
 */
@Service
@RequiredArgsConstructor
public class InternalFinanceDataService {

    private static final List<InvoiceStatus> SETTLED_INVOICE_STATUSES = List.of(InvoiceStatus.PAID, InvoiceStatus.CANCELLED);

    private final ClientInvoiceRepository clientInvoiceRepo;
    private final ShipmentFinancialsRepository financialsRepo;
    private final CarbonOffsetRepository carbonOffsetRepo;

    @Transactional(readOnly = true)
    public DashboardSummaryDto getDashboardSummary(UUID companyId) {
        LocalDate today = LocalDate.now();
        long overdueInvoicesCount = clientInvoiceRepo.countByCompanyIdAndDueDateBeforeAndStatusNotIn(
                companyId, today, SETTLED_INVOICE_STATUSES);
        BigDecimal overdueInvoicesAmount = clientInvoiceRepo.sumTotalAmountByCompanyIdAndDueDateBeforeAndStatusNotIn(
                companyId, today, SETTLED_INVOICE_STATUSES);
        return new DashboardSummaryDto(overdueInvoicesCount, overdueInvoicesAmount);
    }

    @Transactional(readOnly = true)
    public InvoiceTimelinessDto getInvoiceTimeliness(UUID companyId) {
        long opportunities = clientInvoiceRepo.countByCompanyIdAndStatusNot(companyId, InvoiceStatus.DRAFT);
        long defects = clientInvoiceRepo.countByCompanyIdAndDueDateBeforeAndStatusNotIn(
                companyId, LocalDate.now(), SETTLED_INVOICE_STATUSES);
        return new InvoiceTimelinessDto(opportunities, defects);
    }

    @Transactional(readOnly = true)
    public FinancialTotalsDto getFinancialTotals(UUID companyId) {
        return new FinancialTotalsDto(
                financialsRepo.sumRevenueByCompanyId(companyId),
                financialsRepo.sumCostByCompanyId(companyId),
                financialsRepo.sumMarginByCompanyId(companyId));
    }

    @Transactional(readOnly = true)
    public CarbonTotalsDto getCarbonTotals(UUID companyId) {
        return new CarbonTotalsDto(
                carbonOffsetRepo.sumCo2EmissionsKgByCompanyId(companyId),
                carbonOffsetRepo.sumOffsetCreditsRetiredByCompanyId(companyId),
                carbonOffsetRepo.sumOffsetTotalCostByCompanyId(companyId));
    }
}
