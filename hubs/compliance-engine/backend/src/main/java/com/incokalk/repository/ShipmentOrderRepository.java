package com.incokalk.repository;

import com.incokalk.model.ShipmentOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import java.util.Optional;

@Repository
public interface ShipmentOrderRepository extends JpaRepository<ShipmentOrder, UUID> {

    Optional<ShipmentOrder> findByIdAndCompanyId(UUID id, UUID companyId);

    // Portail client : ne renvoie que les expéditions rattachées au client authentifié.
    List<ShipmentOrder> findByCompanyIdAndClientIdOrderByCreatedAtDesc(UUID companyId, UUID clientId);

    Optional<ShipmentOrder> findByIdAndCompanyIdAndClientId(UUID id, UUID companyId, UUID clientId);

    long countByCarrier_Id(UUID carrierId);

    List<ShipmentOrder> findByCompanyIdOrderByCreatedAtDesc(UUID companyId);

    Page<ShipmentOrder> findByCompanyIdOrderByCreatedAtDesc(UUID companyId, Pageable pageable);

    List<ShipmentOrder> findByUserIdOrderByCreatedAtDesc(UUID userId);

    List<ShipmentOrder> findByCompanyIdAndStatus(UUID companyId, ShipmentOrder.Status status);

    List<ShipmentOrder> findByStatus(ShipmentOrder.Status status);

    List<ShipmentOrder> findByCompanyIdAndCreatedAtAfterOrderByCreatedAtDesc(UUID companyId, LocalDateTime after);

    long countByCompanyId(UUID companyId);

    java.util.Optional<ShipmentOrder> findByOrderNumber(String orderNumber);

    @Query("SELECT s.status, COUNT(s) FROM ShipmentOrder s WHERE s.company.id = :companyId GROUP BY s.status")
    List<Object[]> countByStatusGrouped(UUID companyId);

    @Query("SELECT SUM(COALESCE(s.finalCost, s.quotedCost, 0.0)) FROM ShipmentOrder s WHERE s.company.id = :companyId")
    Double sumCostByCompanyId(UUID companyId);

    @Query("SELECT AVG(COALESCE(s.finalCost, s.quotedCost, 0.0)) FROM ShipmentOrder s WHERE s.company.id = :companyId")
    Double avgCostByCompanyId(UUID companyId);

    @EntityGraph(attributePaths = {"carrier", "shippingRate"})
    List<ShipmentOrder> findAnalyticsByCompanyIdOrderByCreatedAtDesc(UUID companyId);

    @EntityGraph(attributePaths = {"carrier", "shippingRate"})
    List<ShipmentOrder> findAnalyticsByCompanyIdAndCreatedAtAfterOrderByCreatedAtDesc(UUID companyId, LocalDateTime after);
}
