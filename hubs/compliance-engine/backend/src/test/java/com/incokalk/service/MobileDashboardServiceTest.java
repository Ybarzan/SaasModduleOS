package com.incokalk.service;

import com.incokalk.model.Company;
import com.incokalk.model.User;
import com.incokalk.repository.ApprovalRequestRepository;
import com.incokalk.repository.CompanyRepository;
import com.incokalk.repository.UserRepository;
import com.incokalk.service.integration.finance.FinanceIntegrationClient;
import com.incokalk.service.integration.fleethub.FleetHubIntegrationClient;
import com.incokalk.service.integration.fleethub.RecentShipmentDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * getRecentActivity et les stats d'expédition de getDashboard restent retirées
 * avec le domaine Transport (ShipmentOrderRepository). pending_approvals reste
 * local ; carbon_offset_month vient désormais de hubs/finance via
 * FinanceIntegrationClient ; active_shipments restauré via
 * FleetHubIntegrationClient (2026-10-29) ; getRecentShipments restauré via
 * FleetHubIntegrationClient.listRecentShipments (2026-10-30, SANS dégradation
 * gracieuse -- voir MobileDashboardService) ; total_shipments/total_cost_month/
 * recent_activity restent non restaurés ; getUserProfile/getCompanyProfile
 * sont inchangées.
 */
@DisplayName("MobileDashboardService — Tests unitaires")
class MobileDashboardServiceTest {

    @Mock ApprovalRequestRepository approvalRequestRepo;
    @Mock FinanceIntegrationClient financeIntegrationClient;
    @Mock FleetHubIntegrationClient fleetHubIntegrationClient;
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
    @DisplayName("getDashboard : renvoie pending_approvals, carbon_offset_month (Finance) et active_shipments (fleet-hub)")
    void getDashboard_returnsSurvivingFields() {
        when(approvalRequestRepo.countByCompanyIdAndStatus(any(), any())).thenReturn(4L);
        when(financeIntegrationClient.getCarbonTotals(companyId)).thenReturn(
                new FinanceIntegrationClient.CarbonTotals(new BigDecimal("123.45"), BigDecimal.ZERO, BigDecimal.ZERO));
        when(fleetHubIntegrationClient.getDashboardSummary(companyId)).thenReturn(
                new FleetHubIntegrationClient.FleetHubDashboardSummary(9, 0, 0));

        Map<String, Object> result = service.getDashboard(companyId);

        assertThat(result.get("pending_approvals")).isEqualTo(4L);
        assertThat(result.get("carbon_offset_month")).isEqualTo(123.45);
        assertThat(result.get("active_shipments")).isEqualTo(9L);
        assertThat(result).doesNotContainKey("total_shipments");
        assertThat(result).doesNotContainKey("total_cost_month");
        assertThat(result).doesNotContainKey("recent_activity");
    }

    @Test
    @DisplayName("getDashboard : Finance et fleet-hub non configurés → dégradation gracieuse (0, pas d'exception)")
    void getDashboard_integrationsUnavailable_gracefulDegradation() {
        when(approvalRequestRepo.countByCompanyIdAndStatus(any(), any())).thenReturn(0L);
        when(financeIntegrationClient.getCarbonTotals(companyId))
                .thenThrow(new FinanceIntegrationClient.FinanceIntegrationException("non configurée"));
        when(fleetHubIntegrationClient.getDashboardSummary(companyId))
                .thenThrow(new FleetHubIntegrationClient.FleetHubIntegrationException("non configurée"));

        Map<String, Object> result = service.getDashboard(companyId);

        assertThat(result.get("carbon_offset_month")).isEqualTo(0.0);
        assertThat(result.get("active_shipments")).isEqualTo(0L);
    }

    @Test
    @DisplayName("getRecentShipments : reformate la liste fleet-hub au format mobile (snake_case, origin/destination imbriqués)")
    void getRecentShipments_mapsToMobileShape() {
        RecentShipmentDto s = new RecentShipmentDto();
        s.setId(UUID.randomUUID());
        s.setOrderNumber("SHP-2026-042");
        s.setStatus("IN_TRANSIT");
        s.setShipperCity("Lyon");
        s.setShipperCountry("FR");
        s.setConsigneeCity("Bruxelles");
        s.setConsigneeCountry("BE");
        s.setGoodsDescription("Textiles");
        s.setWeightKg(12.5);
        s.setIncotermCode("FOB");
        s.setQuotedCost(100.0);
        s.setFinalCost(120.0);
        s.setCurrency("EUR");
        s.setCarrierName("DHL");
        s.setTransportMode("SEA");
        when(fleetHubIntegrationClient.listRecentShipments(companyId)).thenReturn(List.of(s));

        var result = service.getRecentShipments(companyId);

        assertThat(result).hasSize(1);
        Map<String, Object> shipment = result.get(0);
        assertThat(shipment.get("order_number")).isEqualTo("SHP-2026-042");
        assertThat(shipment.get("status")).isEqualTo("IN_TRANSIT");
        assertThat(shipment.get("goods_description")).isEqualTo("Textiles");
        assertThat(shipment.get("weight_kg")).isEqualTo(12.5);
        assertThat(shipment.get("incoterm")).isEqualTo("FOB");
        // finalCost (120) préféré à quotedCost (100) quand les deux sont présents.
        assertThat(shipment.get("total_cost")).isEqualTo(120.0);
        assertThat(shipment.get("carrier_name")).isEqualTo("DHL");
        assertThat(shipment.get("transport_mode")).isEqualTo("SEA");

        @SuppressWarnings("unchecked")
        Map<String, Object> origin = (Map<String, Object>) shipment.get("origin");
        assertThat(origin.get("city")).isEqualTo("Lyon");
        assertThat(origin.get("country")).isEqualTo("FR");

        @SuppressWarnings("unchecked")
        Map<String, Object> destination = (Map<String, Object>) shipment.get("destination");
        assertThat(destination.get("city")).isEqualTo("Bruxelles");
        assertThat(destination.get("country")).isEqualTo("BE");
    }

    @Test
    @DisplayName("getRecentShipments : finalCost absent → repli sur quotedCost")
    void getRecentShipments_fallsBackToQuotedCostWhenFinalCostMissing() {
        RecentShipmentDto s = new RecentShipmentDto();
        s.setQuotedCost(75.0);
        when(fleetHubIntegrationClient.listRecentShipments(companyId)).thenReturn(List.of(s));

        var result = service.getRecentShipments(companyId);

        assertThat(result.get(0).get("total_cost")).isEqualTo(75.0);
    }

    @Test
    @DisplayName("getRecentShipments : panne fleet-hub → propage l'exception (jamais de liste vide trompeuse)")
    void getRecentShipments_fleetHubUnavailable_propagatesException() {
        when(fleetHubIntegrationClient.listRecentShipments(companyId))
                .thenThrow(new FleetHubIntegrationClient.FleetHubIntegrationException("indisponible"));

        assertThatThrownBy(() -> service.getRecentShipments(companyId))
                .isInstanceOf(FleetHubIntegrationClient.FleetHubIntegrationException.class);
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
