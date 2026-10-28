package com.fleethub.repository;

import com.fleethub.model.EtaReportedSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EtaReportedSourceRepository extends JpaRepository<EtaReportedSource, UUID> {

    List<EtaReportedSource> findByCompanyIdOrderByCreatedAtDesc(Long companyId);

    List<EtaReportedSource> findByShipmentId(UUID shipmentId);

    /** Observations comparables à une arrivée réelle -- base de calcul d'EtaSourceReliabilityService. */
    @Query("SELECT ers FROM EtaReportedSource ers JOIN FETCH ers.shipment s "
            + "WHERE ers.source = :source AND ers.reportedArrival IS NOT NULL "
            + "AND s.actualDeliveryDate IS NOT NULL")
    List<EtaReportedSource> findScorable(@Param("source") EtaReportedSource.Source source);
}
