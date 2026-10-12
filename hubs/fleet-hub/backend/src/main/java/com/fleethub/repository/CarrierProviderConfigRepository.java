package com.fleethub.repository;

import com.fleethub.model.CarrierProviderConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CarrierProviderConfigRepository extends JpaRepository<CarrierProviderConfig, Long> {

    Optional<CarrierProviderConfig> findByCompanyIdAndProviderType(Long companyId, String providerType);

    List<CarrierProviderConfig> findByCompanyIdOrderByPriorityAsc(Long companyId);

    List<CarrierProviderConfig> findByCompanyIdAndActiveTrueOrderByPriorityAsc(Long companyId);
}
