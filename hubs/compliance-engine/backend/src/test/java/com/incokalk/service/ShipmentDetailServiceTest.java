package com.incokalk.service;

import com.incokalk.exception.ResourceNotFoundException;
import com.incokalk.service.integration.fleethub.FleetHubIntegrationClient;
import com.incokalk.service.integration.fleethub.ShipmentDetailDto;
import com.incokalk.service.integration.fleethub.ShipmentItemDetailDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("ShipmentDetailService — Tests unitaires")
class ShipmentDetailServiceTest {

    FleetHubIntegrationClient fleetHubIntegrationClient;
    ShipmentDetailService service;
    UUID shipmentId;
    UUID companyId;

    @BeforeEach
    void setUp() {
        fleetHubIntegrationClient = mock(FleetHubIntegrationClient.class);
        service = new ShipmentDetailService(fleetHubIntegrationClient);
        shipmentId = UUID.randomUUID();
        companyId = UUID.randomUUID();
    }

    @Test
    @DisplayName("getDetail → délègue au client et renvoie le DTO")
    void getDetail_delegatesToClient() {
        ShipmentDetailDto dto = new ShipmentDetailDto();
        dto.setOrderNumber("SHP-2026-001");
        when(fleetHubIntegrationClient.getShipmentDetail(shipmentId, companyId)).thenReturn(dto);

        assertThat(service.getDetail(shipmentId, companyId).getOrderNumber()).isEqualTo("SHP-2026-001");
    }

    @Test
    @DisplayName("getDetail → échec fleet-hub devient ResourceNotFoundException")
    void getDetail_fleetHubFailure_becomesResourceNotFound() {
        when(fleetHubIntegrationClient.getShipmentDetail(shipmentId, companyId))
                .thenThrow(new FleetHubIntegrationClient.FleetHubIntegrationException("introuvable"));

        assertThatThrownBy(() -> service.getDetail(shipmentId, companyId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("getItems → délègue au client et renvoie la liste")
    void getItems_delegatesToClient() {
        ShipmentItemDetailDto item = new ShipmentItemDetailDto();
        item.setSku("SKU-1");
        when(fleetHubIntegrationClient.getShipmentItemsDetailed(shipmentId, companyId)).thenReturn(List.of(item));

        assertThat(service.getItems(shipmentId, companyId)).hasSize(1);
        assertThat(service.getItems(shipmentId, companyId).get(0).getSku()).isEqualTo("SKU-1");
    }

    @Test
    @DisplayName("getItems → échec fleet-hub devient ResourceNotFoundException")
    void getItems_fleetHubFailure_becomesResourceNotFound() {
        when(fleetHubIntegrationClient.getShipmentItemsDetailed(shipmentId, companyId))
                .thenThrow(new FleetHubIntegrationClient.FleetHubIntegrationException("indisponible"));

        assertThatThrownBy(() -> service.getItems(shipmentId, companyId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
