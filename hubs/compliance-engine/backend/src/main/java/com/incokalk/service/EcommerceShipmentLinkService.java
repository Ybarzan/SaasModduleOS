package com.incokalk.service;

import com.incokalk.exception.ResourceNotFoundException;
import com.incokalk.model.Company;
import com.incokalk.model.ECommerceIntegration;
import com.incokalk.model.EcommerceShipmentLink;
import com.incokalk.repository.CompanyRepository;
import com.incokalk.repository.EcommerceShipmentLinkRepository;
import com.incokalk.service.integration.fleethub.FleetHubIntegrationClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Corrélation manuelle expédition fleet-hub <-> commande e-commerce --
 * Phase 1 J4 (docs/03-plan-migration.md). {@link #link} vérifie que
 * l'expédition existe réellement côté fleet-hub avant d'enregistrer : jamais
 * de lien vers un UUID non vérifié, même saisi par un opérateur de confiance
 * (faute de frappe, expédition d'une autre société...).
 */
@Service
@RequiredArgsConstructor
public class EcommerceShipmentLinkService {

    private final EcommerceShipmentLinkRepository repo;
    private final CompanyRepository companyRepository;
    private final ECommerceSyncService syncService;
    private final FleetHubIntegrationClient fleetHubClient;

    @Transactional
    public EcommerceShipmentLink link(UUID integrationId, String externalOrderId, UUID shipmentId, UUID companyId) {
        ECommerceIntegration integration = syncService.findIntegrationById(integrationId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Integration not found"));

        try {
            fleetHubClient.getShipmentSummary(shipmentId, companyId);
        } catch (FleetHubIntegrationClient.FleetHubIntegrationException e) {
            throw new IllegalArgumentException(
                    "Impossible de vérifier cette expédition côté fleet-hub : " + e.getMessage());
        }

        Company company = companyRepository.getReferenceById(companyId);

        // Un lien existant pour cette commande ou cette expédition est remplacé
        // plutôt que de laisser la contrainte unique échouer en 500 -- mêmes
        // clés (integration_id, externalOrderId) ou (fleethubShipmentId) que
        // les contraintes de V81.
        repo.findByIntegrationIdAndExternalOrderId(integrationId, externalOrderId).ifPresent(repo::delete);
        repo.findByCompanyIdAndFleethubShipmentId(companyId, shipmentId).ifPresent(repo::delete);

        EcommerceShipmentLink saved = EcommerceShipmentLink.builder()
                .company(company)
                .integration(integration)
                .externalOrderId(externalOrderId)
                .fleethubShipmentId(shipmentId)
                .build();
        return repo.save(saved);
    }

    @Transactional
    public void unlink(UUID integrationId, String externalOrderId, UUID companyId) {
        syncService.findIntegrationById(integrationId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Integration not found"));
        repo.deleteByIntegrationIdAndExternalOrderId(integrationId, externalOrderId);
    }

    @Transactional(readOnly = true)
    public Optional<EcommerceShipmentLink> findForShipment(UUID companyId, UUID shipmentId) {
        return repo.findByCompanyIdAndFleethubShipmentId(companyId, shipmentId);
    }
}
