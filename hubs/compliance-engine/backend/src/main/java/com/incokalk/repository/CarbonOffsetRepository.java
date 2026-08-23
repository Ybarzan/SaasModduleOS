package com.incokalk.repository;

import com.incokalk.model.CarbonOffset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CarbonOffsetRepository extends JpaRepository<CarbonOffset, UUID> {

    List<CarbonOffset> findByCompanyIdOrderByCreatedAtDesc(UUID companyId);

    Optional<CarbonOffset> findByCompanyIdAndId(UUID companyId, UUID id);

    Optional<CarbonOffset> findByShipmentId(UUID shipmentId);

    long countByCompanyIdAndStatus(UUID companyId, CarbonOffset.OffsetStatus status);

    @Query("SELECT COALESCE(SUM(c.co2EmissionsKg), 0) FROM CarbonOffset c WHERE c.company.id = :companyId")
    BigDecimal sumCo2EmissionsKgByCompanyId(@Param("companyId") UUID companyId);

    @Query("SELECT COALESCE(SUM(c.offsetCreditsRetired), 0) FROM CarbonOffset c WHERE c.company.id = :companyId")
    BigDecimal sumOffsetCreditsRetiredByCompanyId(@Param("companyId") UUID companyId);

    @Query("SELECT COALESCE(SUM(c.offsetTotalCost), 0) FROM CarbonOffset c WHERE c.company.id = :companyId")
    BigDecimal sumOffsetTotalCostByCompanyId(@Param("companyId") UUID companyId);

    @Query("SELECT COALESCE(SUM(c.offsetCreditsPurchased), 0) FROM CarbonOffset c WHERE c.company.id = :companyId")
    BigDecimal sumOffsetCreditsPurchasedByCompanyId(@Param("companyId") UUID companyId);
}
