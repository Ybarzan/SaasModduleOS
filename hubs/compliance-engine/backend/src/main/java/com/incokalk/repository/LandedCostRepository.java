package com.incokalk.repository;

import com.incokalk.model.LandedCost;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LandedCostRepository extends JpaRepository<LandedCost, UUID> {

    List<LandedCost> findByCompanyIdOrderByCreatedAtDesc(UUID companyId);

    Optional<LandedCost> findByCompanyIdAndId(UUID companyId, UUID id);

    Optional<LandedCost> findByCompanyIdAndShipmentId(UUID companyId, UUID shipmentId);

    Optional<LandedCost> findByShareToken(String shareToken);

    long countByCompanyId(UUID companyId);
}
