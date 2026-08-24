package com.incokalk.repository;

import com.incokalk.model.FleetHubConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FleetHubConfigRepository extends JpaRepository<FleetHubConfig, UUID> {

    List<FleetHubConfig> findByCompanyIdOrderByCreatedAtDesc(UUID companyId);

    List<FleetHubConfig> findByCompanyIdAndIsActiveTrue(UUID companyId);

    Optional<FleetHubConfig> findByIdAndCompanyId(UUID id, UUID companyId);
}
