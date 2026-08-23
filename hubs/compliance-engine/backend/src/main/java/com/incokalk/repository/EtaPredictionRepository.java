package com.incokalk.repository;

import com.incokalk.model.EtaPrediction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EtaPredictionRepository extends JpaRepository<EtaPrediction, UUID> {

    List<EtaPrediction> findByCompanyIdOrderByCreatedAtDesc(UUID companyId);

    Optional<EtaPrediction> findByCompanyIdAndId(UUID companyId, UUID id);

    Optional<EtaPrediction> findByCompanyIdAndShipmentId(UUID companyId, UUID shipmentId);

    List<EtaPrediction> findByCompanyIdAndOriginAndDestination(UUID companyId, String origin, String destination);

    long countByCompanyId(UUID companyId);

    List<EtaPrediction> findByCompanyIdAndActualDaysNotNullAndPredictionAccuracyNotNull(UUID companyId);
}
