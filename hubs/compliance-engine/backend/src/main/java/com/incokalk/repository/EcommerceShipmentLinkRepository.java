package com.incokalk.repository;

import com.incokalk.model.EcommerceShipmentLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EcommerceShipmentLinkRepository extends JpaRepository<EcommerceShipmentLink, UUID> {

    Optional<EcommerceShipmentLink> findByCompanyIdAndFleethubShipmentId(UUID companyId, UUID fleethubShipmentId);

    Optional<EcommerceShipmentLink> findByIntegrationIdAndExternalOrderId(UUID integrationId, String externalOrderId);

    void deleteByIntegrationIdAndExternalOrderId(UUID integrationId, String externalOrderId);
}
