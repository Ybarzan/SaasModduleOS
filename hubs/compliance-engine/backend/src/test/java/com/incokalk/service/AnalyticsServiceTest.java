package com.incokalk.service;

import com.incokalk.dto.analytics.ChartDataDTO;
import com.incokalk.dto.analytics.DashboardStatsDTO;
import com.incokalk.dto.financial.CostByCarrierDTO;
import com.incokalk.dto.financial.CostTrendDTO;
import com.incokalk.dto.shipment.CarrierPerformanceDTO;
import com.incokalk.dto.shipment.ShipmentByStatusDTO;
import com.incokalk.dto.shipment.ShipmentsOverTimeDTO;
import com.incokalk.dto.shipment.TopRouteDTO;
import com.incokalk.model.Carrier;
import com.incokalk.model.ShipmentOrder;
import com.incokalk.model.ShipmentOrder.Status;
import com.incokalk.model.ShippingRate;
import com.incokalk.repository.CarrierRepository;
import com.incokalk.repository.ShipmentOrderRepository;
import com.incokalk.repository.SimulationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("AnalyticsService — Tests unitaires")
class AnalyticsServiceTest {

    @Mock ShipmentOrderRepository shipmentRepo;
    @Mock CarrierRepository carrierRepo;
    @Mock SimulationRepository simulationRepo;
    @InjectMocks AnalyticsService service;

    UUID companyId;
    ShipmentOrder shipment1;
    ShipmentOrder shipment2;
    Carrier carrier;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        companyId = UUID.randomUUID();
        carrier = Carrier.builder().id(UUID.randomUUID()).name("DHL").code("DHL").transportModes("AIR").build();

        shipment1 = ShipmentOrder.builder()
                .id(UUID.randomUUID())
                .status(Status.DELIVERED)
                .quotedCost(1000.0)
                .finalCost(950.0)
                .weightKg(25.0)
                .volumeM3(1.5)
                .goodsValue(5000.0)
                .shipperCountry("FR")
                .consigneeCountry("US")
                .incotermCode("CIF")
                .carrier(carrier)
                .createdAt(LocalDateTime.now())
                .build();

        shipment2 = ShipmentOrder.builder()
                .id(UUID.randomUUID())
                .status(Status.IN_TRANSIT)
                .quotedCost(2000.0)
                .finalCost(null)
                .weightKg(75.0)
                .volumeM3(3.0)
                .goodsValue(10000.0)
                .shipperCountry("DE")
                .consigneeCountry("CN")
                .incotermCode("FOB")
                .carrier(null)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("getDashboardStats → computes stats for 'all' period")
    void getDashboardStats_allPeriod() {
        when(shipmentRepo.findAnalyticsByCompanyIdOrderByCreatedAtDesc(companyId))
                .thenReturn(List.of(shipment1, shipment2));
        when(carrierRepo.countByCompanyId(companyId)).thenReturn(2L);
        when(carrierRepo.countByCompanyIdAndIsActiveTrue(companyId)).thenReturn(1L);
        when(simulationRepo.countByCompanyId(companyId)).thenReturn(10L);
        when(simulationRepo.countByCompanyIdAndCreatedAtAfter(eq(companyId), any(LocalDateTime.class))).thenReturn(3L);

        DashboardStatsDTO stats = service.getDashboardStats(companyId, "all");
        assertThat(stats.getTotalShipments()).isEqualTo(2);
        assertThat(stats.getActiveShipments()).isEqualTo(1);
        assertThat(stats.getDeliveredShipments()).isEqualTo(1);
        assertThat(stats.getDraftShipments()).isEqualTo(0);
        assertThat(stats.getCancelledShipments()).isEqualTo(0);
        assertThat(stats.getTotalShippingCost()).isEqualTo(2950.0);
        assertThat(stats.getTotalGoodsValue()).isEqualTo(15000.0);
        assertThat(stats.getTotalCarriers()).isEqualTo(2L);
        assertThat(stats.getSimulationsThisMonth()).isEqualTo(3L);
    }

    @Test
    @DisplayName("getDashboardStats → computes stats for '7d' period")
    void getDashboardStats_7dPeriod() {
        when(shipmentRepo.findAnalyticsByCompanyIdAndCreatedAtAfterOrderByCreatedAtDesc(eq(companyId), any(LocalDateTime.class)))
                .thenReturn(List.of(shipment1));
        when(carrierRepo.countByCompanyId(companyId)).thenReturn(1L);
        when(carrierRepo.countByCompanyIdAndIsActiveTrue(companyId)).thenReturn(1L);
        when(simulationRepo.countByCompanyId(companyId)).thenReturn(5L);
        when(simulationRepo.countByCompanyIdAndCreatedAtAfter(eq(companyId), any(LocalDateTime.class))).thenReturn(2L);

        DashboardStatsDTO stats = service.getDashboardStats(companyId, "7d");
        assertThat(stats.getTotalShipments()).isEqualTo(1);
        assertThat(stats.getDeliveredShipments()).isEqualTo(1);
        assertThat(stats.getTotalShippingCost()).isEqualTo(950.0);
    }

    @Test
    @DisplayName("getShipmentsByStatus → returns all statuses with counts")
    void getShipmentsByStatus() {
        when(shipmentRepo.findAnalyticsByCompanyIdOrderByCreatedAtDesc(companyId))
                .thenReturn(List.of(shipment1, shipment2));

        List<ShipmentByStatusDTO> result = service.getShipmentsByStatus(companyId);
        assertThat(result).hasSize(Status.values().length);

        var delivered = result.stream().filter(d -> d.getStatus().equals("DELIVERED")).findFirst().orElseThrow();
        assertThat(delivered.getCount()).isEqualTo(1);
        assertThat(delivered.getPercentage()).isEqualTo(50.0);

        var inTransit = result.stream().filter(d -> d.getStatus().equals("IN_TRANSIT")).findFirst().orElseThrow();
        assertThat(inTransit.getCount()).isEqualTo(1);
        assertThat(inTransit.getPercentage()).isEqualTo(50.0);
    }

    @Test
    @DisplayName("getCostByCarrier → groups by carrier")
    void getCostByCarrier() {
        when(shipmentRepo.findAnalyticsByCompanyIdOrderByCreatedAtDesc(companyId))
                .thenReturn(List.of(shipment1, shipment2));
        when(carrierRepo.findByCompanyIdOrderByCreatedAtDesc(companyId)).thenReturn(List.of(carrier));

        List<CostByCarrierDTO> result = service.getCostByCarrier(companyId);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCarrierName()).isEqualTo("DHL");
        assertThat(result.get(0).getTotalCost()).isEqualTo(950.0);
        assertThat(result.get(0).getAverageCost()).isEqualTo(950.0);
    }

    @Test
    @DisplayName("getWeightDistribution → buckets weights correctly")
    void getWeightDistribution() {
        when(shipmentRepo.findAnalyticsByCompanyIdOrderByCreatedAtDesc(companyId))
                .thenReturn(List.of(shipment1, shipment2));

        ChartDataDTO result = service.getWeightDistribution(companyId);
        assertThat(result.getLabels()).containsExactly("0-10kg", "10-50kg", "50-100kg", "100-500kg", "500+kg");
        assertThat(result.getValues()).containsExactly(0.0, 1.0, 1.0, 0.0, 0.0);
        assertThat(result.getTitle()).isEqualTo("Weight Distribution");
    }

    @Test
    @DisplayName("getVolumeDistribution → buckets volumes correctly")
    void getVolumeDistribution() {
        when(shipmentRepo.findAnalyticsByCompanyIdOrderByCreatedAtDesc(companyId))
                .thenReturn(List.of(shipment1, shipment2));

        ChartDataDTO result = service.getVolumeDistribution(companyId);
        assertThat(result.getValues()).containsExactly(0.0, 0.0, 2.0, 0.0, 0.0);
    }

    @Test
    @DisplayName("getTopRoutes → groups by origin-destination")
    void getTopRoutes() {
        when(shipmentRepo.findAnalyticsByCompanyIdOrderByCreatedAtDesc(companyId))
                .thenReturn(List.of(shipment1, shipment2));

        List<TopRouteDTO> result = service.getTopRoutes(companyId, 10);
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getOrigin()).isEqualTo("FR");
        assertThat(result.get(0).getDestination()).isEqualTo("US");
    }

    @Test
    @DisplayName("getCostTrends → groups by day")
    void getCostTrends() {
        when(shipmentRepo.findAnalyticsByCompanyIdOrderByCreatedAtDesc(companyId))
                .thenReturn(List.of(shipment1, shipment2));

        List<CostTrendDTO> result = service.getCostTrends(companyId, "all", "day");
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTotalCost()).isEqualTo(2950.0);
        assertThat(result.get(0).getShipmentCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("getCarrierPerformance → computes performance metrics")
    void getCarrierPerformance() {
        when(shipmentRepo.findAnalyticsByCompanyIdOrderByCreatedAtDesc(companyId))
                .thenReturn(List.of(shipment1));

        List<CarrierPerformanceDTO> result = service.getCarrierPerformance(companyId);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCarrierName()).isEqualTo("DHL");
        assertThat(result.get(0).getTotalShipments()).isEqualTo(1);
        assertThat(result.get(0).getDelivered()).isEqualTo(1);
        assertThat(result.get(0).getOnTimeRate()).isEqualTo(100.0);
    }
}
