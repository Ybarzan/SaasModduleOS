package com.fleethub.repository;

import com.fleethub.model.EtaReportedSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EtaReportedSourceRepository extends JpaRepository<EtaReportedSource, UUID> {

    List<EtaReportedSource> findByCompanyIdOrderByCreatedAtDesc(Long companyId);

    List<EtaReportedSource> findByShipmentId(UUID shipmentId);

    /** Pour l'upsert d'AisEtaCaptureService : au plus une observation AIS_BROADCAST
     *  par expédition, toujours mise à jour avec la dernière ETA diffusée connue
     *  plutôt qu'une ligne par cycle de capture (voir AisEtaCaptureService). */
    Optional<EtaReportedSource> findByShipmentIdAndSource(UUID shipmentId, EtaReportedSource.Source source);

    /** Observations comparables à une arrivée réelle -- base de calcul
     *  d'EtaSourceReliabilityService. Toutes sources mélangées (CARRIER_BOOKING
     *  et AIS_BROADCAST) dans une seule mesure par groupe transporteur×lane×mode
     *  -- pas de ventilation par source pour l'instant : ça demanderait aussi de
     *  choisir, au moment de la prédiction, quelle source privilégier quand
     *  plusieurs sont disponibles -- hors scope ici (voir EtaSourceReliabilityService). */
    @Query("SELECT ers FROM EtaReportedSource ers JOIN FETCH ers.shipment s "
            + "WHERE ers.reportedArrival IS NOT NULL AND s.actualDeliveryDate IS NOT NULL")
    List<EtaReportedSource> findScorable();
}
