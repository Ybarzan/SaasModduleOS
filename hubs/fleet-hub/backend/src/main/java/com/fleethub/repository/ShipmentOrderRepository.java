package com.fleethub.repository;

import com.fleethub.model.ShipmentOrder;
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

    Optional<ShipmentOrder> findByIdAndCompanyId(UUID id, Long companyId);

    // Portail client : ne renvoie que les expéditions rattachées au client authentifié.
    List<ShipmentOrder> findByCompanyIdAndClientIdOrderByCreatedAtDesc(Long companyId, UUID clientId);

    Optional<ShipmentOrder> findByIdAndCompanyIdAndClientId(UUID id, Long companyId, UUID clientId);

    long countByCarrier_Id(UUID carrierId);

    List<ShipmentOrder> findByCompanyIdOrderByCreatedAtDesc(Long companyId);

    Page<ShipmentOrder> findByCompanyIdOrderByCreatedAtDesc(Long companyId, Pageable pageable);

    List<ShipmentOrder> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<ShipmentOrder> findByCompanyIdAndStatus(Long companyId, ShipmentOrder.Status status);

    List<ShipmentOrder> findByStatus(ShipmentOrder.Status status);

    List<ShipmentOrder> findByCompanyIdAndCreatedAtAfterOrderByCreatedAtDesc(Long companyId, LocalDateTime after);

    long countByCompanyId(Long companyId);

    long countByCompanyIdAndStatusIn(Long companyId, List<ShipmentOrder.Status> statuses);

    java.util.Optional<ShipmentOrder> findByOrderNumber(String orderNumber);

    /** Expéditions liées à un navire suivi (MMSI renseigné) et encore en cours
     *  -- base de AisEtaCaptureService.captureBroadcastEtas(). */
    List<ShipmentOrder> findByTrackedVesselMmsiIsNotNullAndStatusIn(List<ShipmentOrder.Status> statuses);

    @Query("SELECT s.status, COUNT(s) FROM ShipmentOrder s WHERE s.company.id = :companyId GROUP BY s.status")
    List<Object[]> countByStatusGrouped(Long companyId);

    @Query("SELECT SUM(COALESCE(s.finalCost, s.quotedCost, 0.0)) FROM ShipmentOrder s WHERE s.company.id = :companyId")
    Double sumCostByCompanyId(Long companyId);

    @Query("SELECT AVG(COALESCE(s.finalCost, s.quotedCost, 0.0)) FROM ShipmentOrder s WHERE s.company.id = :companyId")
    Double avgCostByCompanyId(Long companyId);

    @EntityGraph(attributePaths = {"carrier", "shippingRate"})
    List<ShipmentOrder> findAnalyticsByCompanyIdOrderByCreatedAtDesc(Long companyId);

    @EntityGraph(attributePaths = {"carrier", "shippingRate"})
    List<ShipmentOrder> findAnalyticsByCompanyIdAndCreatedAtAfterOrderByCreatedAtDesc(Long companyId, LocalDateTime after);
}
