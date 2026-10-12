package com.incokalk.service;

import com.incokalk.model.Company;
import com.incokalk.model.User;
import com.incokalk.repository.ApprovalRequestRepository;
import com.incokalk.repository.CarbonOffsetRepository;
import com.incokalk.repository.CompanyRepository;
import com.incokalk.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * getRecentShipments/getRecentActivity et les stats d'expédition de getDashboard
 * ont été retirées avec le domaine Transport (ShipmentOrderRepository) — voir
 * MobileDashboardService. Seuls pending_approvals/carbon_offset_month survivent
 * dans getDashboard ; getUserProfile/getCompanyProfile sont inchangées.
 */
@DisplayName("MobileDashboardService — Tests unitaires")
class MobileDashboardServiceTest {

    @Mock ApprovalRequestRepository approvalRequestRepo;
    @Mock CarbonOffsetRepository carbonOffsetRepo;
    @Mock UserRepository userRepo;
    @Mock CompanyRepository companyRepo;

    @InjectMocks MobileDashboardService service;

    private UUID companyId;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        companyId = UUID.randomUUID();
    }

    @Test
    @DisplayName("getDashboard : renvoie pending_approvals et carbon_offset_month")
    void getDashboard_returnsSurvivingFields() {
        when(approvalRequestRepo.countByCompanyIdAndStatus(any(), any())).thenReturn(4L);
        when(carbonOffsetRepo.sumCo2EmissionsKgByCompanyId(companyId)).thenReturn(new BigDecimal("123.45"));

        Map<String, Object> result = service.getDashboard(companyId);

        assertThat(result.get("pending_approvals")).isEqualTo(4L);
        assertThat(result.get("carbon_offset_month")).isEqualTo(123.45);
        assertThat(result).doesNotContainKey("total_shipments");
        assertThat(result).doesNotContainKey("active_shipments");
        assertThat(result).doesNotContainKey("total_cost_month");
        assertThat(result).doesNotContainKey("recent_activity");
    }

    @Test
    @DisplayName("getDashboard : carbon_offset_month null → 0.0")
    void getDashboard_nullCarbonOffset_returnsZero() {
        when(approvalRequestRepo.countByCompanyIdAndStatus(any(), any())).thenReturn(0L);
        when(carbonOffsetRepo.sumCo2EmissionsKgByCompanyId(companyId)).thenReturn(null);

        Map<String, Object> result = service.getDashboard(companyId);

        assertThat(result.get("carbon_offset_month")).isEqualTo(0.0);
    }

    @Test
    @DisplayName("getUserProfile : trouvé")
    void getUserProfile_found() {
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).email("u@test.com").build();
        when(userRepo.findById(userId)).thenReturn(Optional.of(user));

        assertThat(service.getUserProfile(userId)).isEqualTo(user);
    }

    @Test
    @DisplayName("getCompanyProfile : trouvé")
    void getCompanyProfile_found() {
        Company company = Company.builder().id(companyId).name("TestCo").build();
        when(companyRepo.findById(companyId)).thenReturn(Optional.of(company));

        assertThat(service.getCompanyProfile(companyId)).isEqualTo(company);
    }
}
