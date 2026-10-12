package com.logistix.service.integration.fleethub;

import com.logistix.model.Company;
import com.logistix.repository.CompanyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("FleetHubIntegrationClient — Authentification par clé d'intégration")
class FleetHubIntegrationClientTest {

    private RestTemplate restTemplate;
    private CompanyRepository companyRepository;
    private FleetHubIntegrationClient client;
    private UUID companyId;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        companyRepository = mock(CompanyRepository.class);
        client = new FleetHubIntegrationClient(restTemplate, companyRepository);
        ReflectionTestUtils.setField(client, "baseUrl", "http://fleet-hub.test");
        companyId = UUID.randomUUID();
    }

    @Test
    @DisplayName("getShipmentItems → société sans clé configurée → erreur explicite, aucun appel HTTP")
    void getShipmentItems_noKeyConfigured_throwsWithoutCallingFleetHub() {
        when(companyRepository.findById(companyId)).thenReturn(Optional.of(Company.builder().id(companyId).build()));

        assertThatThrownBy(() -> client.getShipmentItems(UUID.randomUUID(), companyId))
                .isInstanceOf(FleetHubIntegrationClient.FleetHubIntegrationException.class)
                .hasMessageContaining("non configurée");

        verifyNoInteractions(restTemplate);
    }

    @Test
    @DisplayName("getShipmentItems → société introuvable → erreur explicite, aucun appel HTTP")
    void getShipmentItems_companyNotFound_throwsWithoutCallingFleetHub() {
        when(companyRepository.findById(companyId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> client.getShipmentItems(UUID.randomUUID(), companyId))
                .isInstanceOf(FleetHubIntegrationClient.FleetHubIntegrationException.class)
                .hasMessageContaining("non configurée");

        verifyNoInteractions(restTemplate);
    }

    @Test
    @DisplayName("getShipmentItems → clé configurée → envoie X-Internal-Integration-Key vers /api/internal/shipments/{id}/items")
    void getShipmentItems_withKey_sendsKeyHeaderToInternalEndpoint() {
        UUID shipmentId = UUID.randomUUID();
        when(companyRepository.findById(companyId))
                .thenReturn(Optional.of(Company.builder().id(companyId).fleetHubApiKey("the-key-123").build()));

        ShipmentItemSummary item = ShipmentItemSummary.builder().sku("SKU-1").build();
        when(restTemplate.exchange(eq("http://fleet-hub.test/api/internal/shipments/" + shipmentId + "/items"),
                eq(HttpMethod.GET), any(HttpEntity.class), eq(ShipmentItemSummary[].class)))
                .thenReturn(new ResponseEntity<>(new ShipmentItemSummary[]{item}, HttpStatus.OK));

        var result = client.getShipmentItems(shipmentId, companyId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSku()).isEqualTo("SKU-1");
        verify(restTemplate).exchange(
                eq("http://fleet-hub.test/api/internal/shipments/" + shipmentId + "/items"),
                eq(HttpMethod.GET),
                argThat((HttpEntity<?> entity) -> "the-key-123".equals(entity.getHeaders().getFirst("X-Internal-Integration-Key"))),
                eq(ShipmentItemSummary[].class));
    }

    @Test
    @DisplayName("getShipmentItems → fleet-hub renvoie 401 (clé invalide côté fleet-hub) → erreur propagée")
    void getShipmentItems_fleetHubRejectsKey_propagatesError() {
        UUID shipmentId = UUID.randomUUID();
        when(companyRepository.findById(companyId))
                .thenReturn(Optional.of(Company.builder().id(companyId).fleetHubApiKey("stale-key").build()));
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(ShipmentItemSummary[].class)))
                .thenThrow(new org.springframework.web.client.HttpClientErrorException(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> client.getShipmentItems(shipmentId, companyId))
                .isInstanceOf(FleetHubIntegrationClient.FleetHubIntegrationException.class);
    }
}
