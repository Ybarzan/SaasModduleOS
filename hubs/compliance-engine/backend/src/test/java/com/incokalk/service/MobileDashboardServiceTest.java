package com.incokalk.service;

import com.incokalk.model.ShipmentOrder;
import com.incokalk.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@DisplayName("MobileDashboardService — Tests unitaires")
class MobileDashboardServiceTest {

    @Mock ShipmentOrderRepository shipmentRepo;
    @Mock ApprovalRequestRepository approvalRequestRepo;
    @Mock CarbonOffsetRepository carbonOffsetRepo;
    @Mock UserRepository userRepo;
    @Mock CompanyRepository companyRepo;
    @Mock EntityManager entityManager;

    @InjectMocks MobileDashboardService service;

    private UUID companyId;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        companyId = UUID.randomUUID();
    }

    @Test
    @DisplayName("getRecentShipments : origine/destination partiellement renseignees → pas de NPE (Map.of rejette les valeurs nulles)")
    void getRecentShipments_partialAddress_doesNotThrow() {
        ShipmentOrder shipment = ShipmentOrder.builder()
                .id(UUID.randomUUID())
                .orderNumber("SHP-TEST-0001")
                .status(ShipmentOrder.Status.DRAFT)
                .shipperCountry("FR")
                .shipperCity(null)
                .consigneeCountry(null)
                .consigneeCity(null)
                .build();
        Page<ShipmentOrder> page = new PageImpl<>(List.of(shipment));
        when(shipmentRepo.findByCompanyIdOrderByCreatedAtDesc(any(UUID.class), any(Pageable.class))).thenReturn(page);

        List<Map<String, Object>> result = service.getRecentShipments(companyId, 20);

        assertThat(result).hasSize(1);
        @SuppressWarnings("unchecked")
        Map<String, Object> origin = (Map<String, Object>) result.get(0).get("origin");
        @SuppressWarnings("unchecked")
        Map<String, Object> destination = (Map<String, Object>) result.get(0).get("destination");
        assertThat(origin).containsEntry("country", "FR").containsEntry("city", null);
        assertThat(destination).containsEntry("country", null).containsEntry("city", null);
    }

    @Test
    @DisplayName("getRecentShipments : aucune expedition → liste vide")
    void getRecentShipments_noShipments_returnsEmptyList() {
        when(shipmentRepo.findByCompanyIdOrderByCreatedAtDesc(any(UUID.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        List<Map<String, Object>> result = service.getRecentShipments(companyId, 20);

        assertThat(result).isEmpty();
    }
}
