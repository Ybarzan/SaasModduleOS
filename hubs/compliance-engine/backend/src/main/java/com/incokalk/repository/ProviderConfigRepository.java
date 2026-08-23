package com.incokalk.repository;

import com.incokalk.model.ProviderConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProviderConfigRepository extends JpaRepository<ProviderConfig, UUID> {

    List<ProviderConfig> findByCompanyIdOrderByPriorityAsc(UUID companyId);

    List<ProviderConfig> findByCompanyIdAndIsActiveTrueOrderByPriorityAsc(UUID companyId);

    Optional<ProviderConfig> findByCompanyIdAndProviderType(UUID companyId, String providerType);

    boolean existsByCompanyIdAndProviderType(UUID companyId, String providerType);
}
