package com.fleethub.repository;

import com.fleethub.model.EtaPrediction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EtaPredictionRepository extends JpaRepository<EtaPrediction, UUID> {

    List<EtaPrediction> findByCompanyIdOrderByCreatedAtDesc(Long companyId);

    Optional<EtaPrediction> findByCompanyIdAndId(Long companyId, UUID id);

    Optional<EtaPrediction> findByCompanyIdAndShipmentId(Long companyId, UUID shipmentId);

    List<EtaPrediction> findByCompanyIdAndOriginAndDestination(Long companyId, String origin, String destination);

    long countByCompanyId(Long companyId);

    List<EtaPrediction> findByCompanyIdAndActualDaysNotNullAndPredictionAccuracyNotNull(Long companyId);
}
