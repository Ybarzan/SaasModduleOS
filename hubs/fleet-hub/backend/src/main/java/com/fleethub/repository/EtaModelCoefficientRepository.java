package com.fleethub.repository;

import com.fleethub.model.EtaModelCoefficient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EtaModelCoefficientRepository extends JpaRepository<EtaModelCoefficient, UUID> {

    List<EtaModelCoefficient> findByCompanyIdAndIsActiveTrue(Long companyId);

    Optional<EtaModelCoefficient> findByCompanyIdAndFeatureNameAndFeatureValue(
        Long companyId, String featureName, String featureValue);

    long countByCompanyIdAndIsActiveTrue(Long companyId);

    List<EtaModelCoefficient> findByCompanyIdOrderByTrainedAtDesc(Long companyId);
}
