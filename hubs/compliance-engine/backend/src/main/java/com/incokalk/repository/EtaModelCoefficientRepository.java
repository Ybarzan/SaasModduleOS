package com.incokalk.repository;

import com.incokalk.model.EtaModelCoefficient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EtaModelCoefficientRepository extends JpaRepository<EtaModelCoefficient, UUID> {

    List<EtaModelCoefficient> findByCompanyIdAndIsActiveTrue(UUID companyId);

    Optional<EtaModelCoefficient> findByCompanyIdAndFeatureNameAndFeatureValue(
        UUID companyId, String featureName, String featureValue);

    long countByCompanyIdAndIsActiveTrue(UUID companyId);

    List<EtaModelCoefficient> findByCompanyIdOrderByTrainedAtDesc(UUID companyId);
}
