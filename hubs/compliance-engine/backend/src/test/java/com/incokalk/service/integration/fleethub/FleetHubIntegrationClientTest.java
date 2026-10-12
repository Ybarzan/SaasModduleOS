package com.incokalk.service.integration.fleethub;

import com.incokalk.model.Company;
import com.incokalk.repository.CompanyRepository;
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
    @DisplayName("getShipmentSummary → entreprise sans clé configurée → erreur explicite, aucun appel HTTP")
    void getShipmentSummary_noKeyConfigured_throwsWithoutCallingFleetHub() {
        when(companyRepository.findById(companyId)).thenReturn(Optional.of(Company.builder().id(companyId).build()));

        assertThatThrownBy(() -> client.getShipmentSummary(UUID.randomUUID(), companyId))
                .isInstanceOf(FleetHubIntegrationClient.FleetHubIntegrationException.class)
                .hasMessageContaining("non configurée");

        verifyNoInteractions(restTemplate);
    }

    @Test
    @DisplayName("getShipmentSummary → entreprise introuvable → erreur explicite, aucun appel HTTP")
    void getShipmentSummary_companyNotFound_throwsWithoutCallingFleetHub() {
        when(companyRepository.findById(companyId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> client.getShipmentSummary(UUID.randomUUID(), companyId))
                .isInstanceOf(FleetHubIntegrationClient.FleetHubIntegrationException.class)
                .hasMessageContaining("non configurée");

        verifyNoInteractions(restTemplate);
    }

    @Test
    @DisplayName("getShipmentSummary → clé configurée → envoie X-Internal-Integration-Key vers /api/internal/shipments/{id}")
    void getShipmentSummary_withKey_sendsKeyHeaderToInternalEndpoint() {
        UUID shipmentId = UUID.randomUUID();
        when(companyRepository.findById(companyId))
                .thenReturn(Optional.of(Company.builder().id(companyId).fleetHubApiKey("the-key-123").build()));

        ShipmentSummary summary = ShipmentSummary.builder().shipperName("Shipper SARL").build();
        when(restTemplate.exchange(eq("http://fleet-hub.test/api/internal/shipments/" + shipmentId),
                eq(HttpMethod.GET), any(HttpEntity.class), eq(ShipmentSummary.class)))
                .thenReturn(new ResponseEntity<>(summary, HttpStatus.OK));
        when(restTemplate.exchange(eq("http://fleet-hub.test/api/internal/shipments/" + shipmentId + "/items"),
                eq(HttpMethod.GET), any(HttpEntity.class), eq(ShipmentSummary.Item[].class)))
                .thenReturn(new ResponseEntity<>(new ShipmentSummary.Item[0], HttpStatus.OK));

        ShipmentSummary result = client.getShipmentSummary(shipmentId, companyId);

        assertThat(result.getShipperName()).isEqualTo("Shipper SARL");
        verify(restTemplate).exchange(
                eq("http://fleet-hub.test/api/internal/shipments/" + shipmentId),
                eq(HttpMethod.GET),
                argThat((HttpEntity<?> entity) -> "the-key-123".equals(entity.getHeaders().getFirst("X-Internal-Integration-Key"))),
                eq(ShipmentSummary.class));
    }

    @Test
    @DisplayName("getShipmentSummary → fleet-hub renvoie 401 (clé invalide côté fleet-hub) → erreur propagée")
    void getShipmentSummary_fleetHubRejectsKey_propagatesError() {
        UUID shipmentId = UUID.randomUUID();
        when(companyRepository.findById(companyId))
                .thenReturn(Optional.of(Company.builder().id(companyId).fleetHubApiKey("stale-key").build()));
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(ShipmentSummary.class)))
                .thenThrow(new org.springframework.web.client.HttpClientErrorException(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> client.getShipmentSummary(shipmentId, companyId))
                .isInstanceOf(FleetHubIntegrationClient.FleetHubIntegrationException.class);
    }
}
