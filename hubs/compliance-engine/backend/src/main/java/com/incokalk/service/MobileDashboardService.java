package com.incokalk.service;

import com.incokalk.model.ApprovalRequest;
import com.incokalk.model.Company;
import com.incokalk.model.User;
import com.incokalk.repository.ApprovalRequestRepository;
import com.incokalk.repository.CarbonOffsetRepository;
import com.incokalk.repository.CompanyRepository;
import com.incokalk.repository.UserRepository;
import lombok.RequiredArgsConstructor;
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
 * JPQL brutes). Seuls pending_approvals (ApprovalRequestRepository) et
 * carbon_offset_month (CarbonOffsetRepository) survivent dans getDashboard ;
 * getUserProfile/getCompanyProfile sont inchangees. Une reintroduction future des
 * stats d'expedition necessiterait un appel REST vers fleet-hub plutot qu'une
 * lecture locale.
 */
@Service
@RequiredArgsConstructor
public class MobileDashboardService {

    private final ApprovalRequestRepository approvalRequestRepo;
    private final CarbonOffsetRepository carbonOffsetRepo;
    private final UserRepository userRepo;
    private final CompanyRepository companyRepo;

    @Transactional(readOnly = true)
    public Map<String, Object> getDashboard(UUID companyId) {
        Map<String, Object> result = new LinkedHashMap<>();

        long pendingApprovals = approvalRequestRepo.countByCompanyIdAndStatus(
                companyId, ApprovalRequest.ApprovalStatus.PENDING);
        result.put("pending_approvals", pendingApprovals);

        BigDecimal carbonOffsetMonth = carbonOffsetRepo.sumCo2EmissionsKgByCompanyId(companyId);
        result.put("carbon_offset_month", carbonOffsetMonth != null ? carbonOffsetMonth.doubleValue() : 0.0);

        return result;
    }

    public User getUserProfile(UUID userId) {
        return userRepo.findById(userId).orElse(null);
    }

    public Company getCompanyProfile(UUID companyId) {
        return companyRepo.findById(companyId).orElse(null);
    }
}
