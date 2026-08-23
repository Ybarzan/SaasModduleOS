package com.incokalk.repository;

import com.incokalk.model.ShipmentFinancials;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ShipmentFinancialsRepository extends JpaRepository<ShipmentFinancials, UUID> {

    List<ShipmentFinancials> findByCompanyIdOrderByCreatedAtDesc(UUID companyId);

    Optional<ShipmentFinancials> findByCompanyIdAndId(UUID companyId, UUID id);

    Optional<ShipmentFinancials> findByShipmentId(UUID shipmentId);

    @Query("SELECT COALESCE(SUM(sf.revenue), 0) FROM ShipmentFinancials sf WHERE sf.company.id = :companyId")
    BigDecimal sumRevenueByCompanyId(@Param("companyId") UUID companyId);

    @Query("SELECT COALESCE(SUM(sf.totalCost), 0) FROM ShipmentFinancials sf WHERE sf.company.id = :companyId")
    BigDecimal sumCostByCompanyId(@Param("companyId") UUID companyId);

    @Query("SELECT COALESCE(SUM(sf.grossMargin), 0) FROM ShipmentFinancials sf WHERE sf.company.id = :companyId")
    BigDecimal sumMarginByCompanyId(@Param("companyId") UUID companyId);

    @Query("SELECT sf.carrierName, COALESCE(SUM(sf.revenue), 0) as revenue, COALESCE(SUM(sf.totalCost), 0) as cost, COALESCE(SUM(sf.grossMargin), 0) as margin, COUNT(sf) as shipments FROM ShipmentFinancials sf WHERE sf.company.id = :companyId GROUP BY sf.carrierName ORDER BY margin DESC")
    List<Object[]> profitByCarrier(@Param("companyId") UUID companyId);

    @Query("SELECT CONCAT(sf.origin, ' → ', sf.destination) as lane, COALESCE(SUM(sf.revenue), 0) as revenue, COALESCE(SUM(sf.totalCost), 0) as cost, COALESCE(SUM(sf.grossMargin), 0) as margin, COUNT(sf) as shipments FROM ShipmentFinancials sf WHERE sf.company.id = :companyId GROUP BY sf.origin, sf.destination ORDER BY margin DESC")
    List<Object[]> profitByLane(@Param("companyId") UUID companyId);

    long countByCompanyId(UUID companyId);
}
