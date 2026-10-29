package com.incokalk.service;

import com.incokalk.exception.ResourceNotFoundException;
import com.incokalk.model.Company;
import com.incokalk.model.ECommerceIntegration;
import com.incokalk.model.EcommerceShipmentLink;
import com.incokalk.repository.CompanyRepository;
import com.incokalk.repository.EcommerceShipmentLinkRepository;
import com.incokalk.service.integration.fleethub.FleetHubIntegrationClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Phase 1 J4 complément (docs/03-plan-migration.md) : link() ne doit jamais
 * enregistrer une corrélation vers une expédition non vérifiée côté
 * fleet-hub.
 */
@DisplayName("EcommerceShipmentLinkService — link/unlink")
class EcommerceShipmentLinkServiceTest {

    private EcommerceShipmentLinkRepository repo;
    private CompanyRepository companyRepository;
    private ECommerceSyncService syncService;
    private FleetHubIntegrationClient fleetHubClient;
    private EcommerceShipmentLinkService service;

    private UUID companyId;
    private UUID integrationId;
    private UUID shipmentId;
    private ECommerceIntegration integration;

    @BeforeEach
    void setUp() {
        repo = mock(EcommerceShipmentLinkRepository.class);
        companyRepository = mock(CompanyRepository.class);
        syncService = mock(ECommerceSyncService.class);
        fleetHubClient = mock(FleetHubIntegrationClient.class);
        service = new EcommerceShipmentLinkService(repo, companyRepository, syncService, fleetHubClient);

        companyId = UUID.randomUUID();
        integrationId = UUID.randomUUID();
        shipmentId = UUID.randomUUID();
        integration = ECommerceIntegration.builder()
                .id(integrationId).platform(ECommerceIntegration.Platform.SHOPIFY).build();

        when(syncService.findIntegrationById(integrationId, companyId)).thenReturn(Optional.of(integration));
        when(companyRepository.getReferenceById(companyId)).thenReturn(new Company());
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    @DisplayName("expédition vérifiée côté fleet-hub → lien enregistré")
    void link_verifiedShipment_saves() {
        when(repo.findByIntegrationIdAndExternalOrderId(integrationId, "EXT-1")).thenReturn(Optional.empty());
        when(repo.findByCompanyIdAndFleethubShipmentId(companyId, shipmentId)).thenReturn(Optional.empty());

        EcommerceShipmentLink saved = service.link(integrationId, "EXT-1", shipmentId, companyId);

        assertThat(saved.getExternalOrderId()).isEqualTo("EXT-1");
        assertThat(saved.getFleethubShipmentId()).isEqualTo(shipmentId);
        assertThat(saved.getIntegration()).isEqualTo(integration);
        verify(fleetHubClient).getShipmentSummary(shipmentId, companyId);
    }

    @Test
    @DisplayName("expédition introuvable côté fleet-hub → rejeté, jamais enregistré")
    void link_unverifiableShipment_rejectedWithoutSaving() {
        when(fleetHubClient.getShipmentSummary(shipmentId, companyId))
                .thenThrow(new FleetHubIntegrationClient.FleetHubIntegrationException("Expédition introuvable sur fleet-hub (statut 404 NOT_FOUND)"));

        assertThatThrownBy(() -> service.link(integrationId, "EXT-1", shipmentId, companyId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Expédition introuvable");
        verify(repo, never()).save(any());
    }

    @Test
    @DisplayName("intégration d'une autre société → introuvable")
    void link_wrongCompanyIntegration_notFound() {
        when(syncService.findIntegrationById(integrationId, companyId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.link(integrationId, "EXT-1", shipmentId, companyId))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(fleetHubClient);
    }

    @Test
    @DisplayName("un lien existe déjà pour cette commande ou cette expédition → remplacé, pas de doublon")
    void link_replacesExistingLinks() {
        EcommerceShipmentLink existingByOrder = EcommerceShipmentLink.builder().id(UUID.randomUUID()).build();
        EcommerceShipmentLink existingByShipment = EcommerceShipmentLink.builder().id(UUID.randomUUID()).build();
        when(repo.findByIntegrationIdAndExternalOrderId(integrationId, "EXT-1")).thenReturn(Optional.of(existingByOrder));
        when(repo.findByCompanyIdAndFleethubShipmentId(companyId, shipmentId)).thenReturn(Optional.of(existingByShipment));

        service.link(integrationId, "EXT-1", shipmentId, companyId);

        verify(repo).delete(existingByOrder);
        verify(repo).delete(existingByShipment);
        ArgumentCaptor<EcommerceShipmentLink> captor = ArgumentCaptor.forClass(EcommerceShipmentLink.class);
        verify(repo).save(captor.capture());
        assertThat(captor.getValue().getId()).isNull();
    }

    @Test
    @DisplayName("unlink → supprime la corrélation pour cette intégration/commande")
    void unlink_removesLink() {
        service.unlink(integrationId, "EXT-1", companyId);

        verify(repo).deleteByIntegrationIdAndExternalOrderId(integrationId, "EXT-1");
    }

    @Test
    @DisplayName("unlink sur une intégration d'une autre société → introuvable, rien supprimé")
    void unlink_wrongCompanyIntegration_notFound() {
        when(syncService.findIntegrationById(integrationId, companyId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.unlink(integrationId, "EXT-1", companyId))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(repo, never()).deleteByIntegrationIdAndExternalOrderId(any(), any());
    }

    @Test
    @DisplayName("findForShipment → délègue directement au repository")
    void findForShipment_delegatesToRepo() {
        EcommerceShipmentLink link = EcommerceShipmentLink.builder().id(UUID.randomUUID()).build();
        when(repo.findByCompanyIdAndFleethubShipmentId(companyId, shipmentId)).thenReturn(Optional.of(link));

        assertThat(service.findForShipment(companyId, shipmentId)).contains(link);
    }
}
