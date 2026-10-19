package com.incokalk.service;

import com.incokalk.model.ApprovalRequest;
import com.incokalk.model.Company;
import com.incokalk.model.User;
import com.incokalk.repository.ApprovalRequestRepository;
import com.incokalk.repository.CompanyRepository;
import com.incokalk.repository.UserRepository;
import com.incokalk.service.integration.finance.FinanceIntegrationClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * getRecentShipments/getRecentActivity et les stats d'expedition de getDashboard
 * (total_shipments/active_shipments/total_cost_month) ont ete retirees avec le
 * domaine Transport -- elles lisaient ShipmentOrderRepository directement (requetes
 * JPQL brutes). pending_approvals reste local (ApprovalRequestRepository) ;
 * carbon_offset_month vient desormais de hubs/finance via FinanceIntegrationClient
 * (degradation gracieuse si l'integration n'est pas configuree) ;
 * getUserProfile/getCompanyProfile sont inchangees.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MobileDashboardService {

    private final ApprovalRequestRepository approvalRequestRepo;
    private final FinanceIntegrationClient financeIntegrationClient;
    private final UserRepository userRepo;
    private final CompanyRepository companyRepo;

    @Transactional(readOnly = true)
    public Map<String, Object> getDashboard(UUID companyId) {
        Map<String, Object> result = new LinkedHashMap<>();

        long pendingApprovals = approvalRequestRepo.countByCompanyIdAndStatus(
                companyId, ApprovalRequest.ApprovalStatus.PENDING);
        result.put("pending_approvals", pendingApprovals);

        BigDecimal carbonOffsetMonth = carbonTotalsOrZero(companyId).co2EmissionsKg();
        result.put("carbon_offset_month", carbonOffsetMonth != null ? carbonOffsetMonth.doubleValue() : 0.0);

        return result;
    }

    public User getUserProfile(UUID userId) {
        return userRepo.findById(userId).orElse(null);
    }

    public Company getCompanyProfile(UUID companyId) {
        return companyRepo.findById(companyId).orElse(null);
    }

    /** Dégradation gracieuse : une société sans clé Finance configurée (ou
     * Finance indisponible) n'empêche jamais le reste du tableau de bord
     * mobile de s'afficher -- même principe que CrossHubDashboardService
     * côté Praxio. */
    private FinanceIntegrationClient.CarbonTotals carbonTotalsOrZero(UUID companyId) {
        try {
            return financeIntegrationClient.getCarbonTotals(companyId);
        } catch (FinanceIntegrationClient.FinanceIntegrationException e) {
            log.debug("[Finance] Agrégats carbone indisponibles pour {} : {}", companyId, e.getMessage());
            return new FinanceIntegrationClient.CarbonTotals(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }
    }
}
